/*
 * muse_exec — Muse 内置运行时 exec 垫片（P1-B，LD_PRELOAD）
 * ============================================================================
 * 目标：在应用沙盒（内置 Node 运行时）中，让 npm 生态的「按名字调用
 *      node / npm / npx」以及 shebang 脚本（#!/usr/bin/env node）可以正常执行。
 *
 * 背景（Android W^X）：targetSdk 29+ 的应用不可 exec 数据目录内文件，
 *      npx 装配的 .bin 包装脚本（shebang 指向 node）因此会被 SELinux 拦截
 *      （execute_no_trans denied，exit 126）。本垫片在 exec 入口处截住这类
 *      调用，改写为「直接用内置 node 执行」——目标为 apk_data_file（允许）。
 *
 * 机制：
 *   1. 拦截 execve / execvp / execvpe；
 *   2. 名称映射（仅当目标为「裸名字」，即 PATH 查找语义）：
 *        node / nodejs  ->  $MUSE_NODE_BIN
 *        npm            ->  $MUSE_NODE_BIN $MUSE_NPM_CLI
 *        npx            ->  $MUSE_NODE_BIN $MUSE_NPX_CLI
 *   3. shebang 解析：目标为脚本且首行指向 node 时，改写为
 *        $MUSE_NODE_BIN <script> <原参数...>
 *      （open 跟随 symlink，覆盖 node_modules/.bin 的软链入口）
 *   4. 任何解析失败 / 条件不符 → 原样调用真实函数（防御性：绝不阻断子进程）。
 *
 * 环境变量（由 MuseRuntime.buildEnv 注入）：
 *   MUSE_NODE_BIN = <nativeLibraryDir>/libmuse_node.so
 *   MUSE_NPM_CLI  = <runtimeDir>/npm/bin/npm-cli.js
 *   MUSE_NPX_CLI  = <runtimeDir>/npm/bin/npx-cli.js
 */

#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

typedef int (*execve_fn)(const char *, char *const[], char *const[]);
typedef int (*execvp_fn)(const char *, char *const[]);
typedef int (*execvpe_fn)(const char *, char *const[], char *const[]);

static execve_fn real_execve = NULL;
static execvp_fn real_execvp = NULL;
static execvpe_fn real_execvpe = NULL;

extern char **environ;

static void muse_init(void) {
    if (!real_execve) real_execve = (execve_fn)dlsym(RTLD_NEXT, "execve");
    if (!real_execvp) real_execvp = (execvp_fn)dlsym(RTLD_NEXT, "execvp");
    if (!real_execvpe) real_execvpe = (execvpe_fn)dlsym(RTLD_NEXT, "execvpe");
}

static const char *muse_env(const char *k) {
    const char *v = getenv(k);
    return (v && *v) ? v : NULL;
}

static int muse_argc(char *const argv[]) {
    int n = 0;
    if (!argv) return 0;
    while (argv[n]) n++;
    return n;
}

/* 读取文件首行，判断是否为「指向 node 的 shebang 脚本」。 */
static int muse_is_node_script(const char *path) {
    int fd = open(path, O_RDONLY);
    if (fd < 0) return 0;
    char buf[256];
    ssize_t n = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    if (n <= 2) return 0;
    buf[n] = '\0';
    if (buf[0] != '#' || buf[1] != '!') return 0;
    char *p = buf + 2;
    while (*p == ' ' || *p == '\t') p++;
    /* 形如 #!node / #!/.../node */
    if (strncmp(p, "node", 4) == 0) return 1;
    if (strstr(p, "/node") != NULL) return 1;
    /* 形如 #!/usr/bin/env node */
    if (strstr(p, "env") != NULL && strstr(p, " node") != NULL) return 1;
    return 0;
}

/* 以 node 执行：node [script] <argv[1..]> */
static int muse_exec_with_node(execve_fn fn, const char *node, const char *script,
                               char *const argv[], char *const envp[]) {
    int tail = muse_argc(argv);
    char **nv = (char **)malloc(sizeof(char *) * (size_t)(3 + tail + 1));
    if (!nv) {
        errno = ENOMEM;
        return -1;
    }
    int i = 0;
    nv[i++] = (char *)node;
    if (script) nv[i++] = (char *)script;
    /* argv[0] 是「程序名」语义，跳过；其余参数原样透传。 */
    for (int j = 1; j < tail; j++) nv[i++] = argv[j];
    nv[i] = NULL;
    int rc = fn(node, nv, envp);
    free(nv);
    return rc;
}

/* 尝试改写执行；返回 1 = 已接管（rc 有效），0 = 未接管（调用方原样执行）。 */
static int muse_try_rewrite(const char *path, char *const argv[], char *const envp[],
                            int *rc_out) {
    if (!path || path[0] == '\0') return 0;
    if (!real_execve) return 0;
    const char *node = muse_env("MUSE_NODE_BIN");
    if (!node) return 0;

    /* 含路径分隔符：不做名字映射；仅尝试 shebang 解析。 */
    if (strchr(path, '/') != NULL) {
        if (muse_is_node_script(path)) {
            *rc_out = muse_exec_with_node(real_execve, node, path, argv, envp);
            return 1;
        }
        return 0;
    }

    /* 裸名字映射 */
    if (strcmp(path, "node") == 0 || strcmp(path, "nodejs") == 0) {
        *rc_out = muse_exec_with_node(real_execve, node, NULL, argv, envp);
        return 1;
    }
    if (strcmp(path, "npm") == 0 || strcmp(path, "npx") == 0) {
        const char *cli = muse_env(strcmp(path, "npm") == 0 ? "MUSE_NPM_CLI" : "MUSE_NPX_CLI");
        if (cli) {
            *rc_out = muse_exec_with_node(real_execve, node, cli, argv, envp);
            return 1;
        }
    }
    return 0;
}

int execve(const char *path, char *const argv[], char *const envp[]) {
    muse_init();
    if (!real_execve) {
        errno = ENOSYS;
        return -1;
    }
    int rc = 0;
    if (muse_try_rewrite(path, argv, envp, &rc)) return rc;
    return real_execve(path, argv, envp);
}

int execvp(const char *file, char *const argv[]) {
    muse_init();
    if (!real_execvp) {
        errno = ENOSYS;
        return -1;
    }
    int rc = 0;
    if (muse_try_rewrite(file, argv, environ, &rc)) return rc;
    return real_execvp(file, argv);
}

int execvpe(const char *file, char *const argv[], char *const envp[]) {
    muse_init();
    if (!real_execvpe) {
        errno = ENOSYS;
        return -1;
    }
    int rc = 0;
    if (muse_try_rewrite(file, argv, envp, &rc)) return rc;
    return real_execvpe(file, argv, envp);
}
