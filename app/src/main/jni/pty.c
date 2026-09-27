/*
 * v2.x 终端二期:自编译 PTY 桥。
 *
 * 标准 POSIX forkpty(3) 解法,自写实现(未使用任何第三方代码)。
 * 提供:创建子进程(伪终端)、阻塞读写、进程退出等待、窗口尺寸同步。
 *
 * JNI 侧对应 Kotlin 单例 io.zer0.muse.terminal.Pty。
 * 所有 socket/fd 语义均为裸 fd,Java 侧不做 FileDescriptor 反射——
 * 读写全部经由本文件的 native 函数,规避隐藏 API 限制。
 */
#include <jni.h>
#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <pty.h>
#include <stdlib.h>
#include <termios.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/wait.h>

#define LOG_TAG "MusePty"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define MAX_READ_CHUNK 8192

/**
 * 创建伪终端子进程。
 * @return 主端(master) fd;失败返回 -1。子进程 pid 写入 pidArray[0]。
 */
JNIEXPORT jint JNICALL
Java_io_zer0_muse_terminal_Pty_createSubprocess(
        JNIEnv *env, jobject thiz,
        jstring execPath, jstring cwd, jintArray pidArray) {
    const char *exec_c = (*env)->GetStringUTFChars(env, execPath, NULL);
    const char *cwd_c = (*env)->GetStringUTFChars(env, cwd, NULL);
    if (exec_c == NULL || cwd_c == NULL) {
        if (exec_c) (*env)->ReleaseStringUTFChars(env, execPath, exec_c);
        if (cwd_c) (*env)->ReleaseStringUTFChars(env, cwd, cwd_c);
        return -1;
    }

    struct winsize ws = {24, 80, 0, 0};
    int pid = -1;
    int master = forkpty(&pid, NULL, NULL, &ws);
    if (master < 0) {
        LOGE("forkpty failed: %s", strerror(errno));
        (*env)->ReleaseStringUTFChars(env, execPath, exec_c);
        (*env)->ReleaseStringUTFChars(env, cwd, cwd_c);
        return -1;
    }

    if (pid == 0) {
        /* 子进程:仅做最小 syscall,随后 exec。*/
        setenv("TERM", "xterm-256color", 1);
        setenv("HOME", cwd_c, 1);
        setenv("PATH", "/system/bin:/system/xbin", 1);
        if (cwd_c[0] != '\0') {
            if (chdir(cwd_c) != 0) {
                chdir("/");
            }
        }
        execl(exec_c, "sh", (char *) NULL);
        _exit(127);
    }

    /* 父进程 */
    jint pidVal = pid;
    (*env)->SetIntArrayRegion(env, pidArray, 0, 1, &pidVal);
    (*env)->ReleaseStringUTFChars(env, execPath, exec_c);
    (*env)->ReleaseStringUTFChars(env, cwd, cwd_c);
    LOGI("pty created: pid=%d master=%d", pid, master);
    return master;
}

/**
 * 阻塞等待子进程退出;返回退出码(信号终止返回 128+signo)。
 */
JNIEXPORT jint JNICALL
Java_io_zer0_muse_terminal_Pty_waitFor(JNIEnv *env, jobject thiz, jint pid) {
    int status = 0;
    int r;
    do {
        r = waitpid(pid, &status, 0);
    } while (r < 0 && errno == EINTR);
    if (r < 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

/**
 * 从 pty 主端读取(阻塞)。返回读到的字节数;<=0 表示 EOF/错误。
 */
JNIEXPORT jint JNICALL
Java_io_zer0_muse_terminal_Pty_read(
        JNIEnv *env, jobject thiz,
        jint fd, jbyteArray buffer, jint offset, jint length) {
    int cap = length > MAX_READ_CHUNK ? MAX_READ_CHUNK : length;
    if (cap <= 0) return 0;
    jbyte tmp[MAX_READ_CHUNK];
    int n = (int) read(fd, tmp, (size_t) cap);
    if (n > 0) {
        (*env)->SetByteArrayRegion(env, buffer, offset, n, tmp);
    }
    return n;
}

/**
 * 向 pty 主端写入,内部处理部分写与 EINTR。返回写入总字节数(<0 出错)。
 */
JNIEXPORT jint JNICALL
Java_io_zer0_muse_terminal_Pty_write(
        JNIEnv *env, jobject thiz,
        jint fd, jbyteArray data, jint offset, jint length) {
    if (length <= 0) return 0;
    jbyte *p = (*env)->GetByteArrayElements(env, data, NULL);
    if (p == NULL) return -1;
    int total = 0;
    while (total < length) {
        ssize_t n = write(fd, (const char *) p + offset + total, (size_t) (length - total));
        if (n < 0) {
            if (errno == EINTR) continue;
            total = -1;
            break;
        }
        total += (int) n;
    }
    (*env)->ReleaseByteArrayElements(env, data, p, JNI_ABORT);
    return total;
}

/** 主端当前可读字节数(FIONREAD);出错返回 -1。 */
JNIEXPORT jint JNICALL
Java_io_zer0_muse_terminal_Pty_available(JNIEnv *env, jobject thiz, jint fd) {
    int n = 0;
    if (ioctl(fd, FIONREAD, &n) < 0) return -1;
    return n;
}

/** 同步终端窗口尺寸(rows/cols),让全屏程序正确排版。 */
JNIEXPORT void JNICALL
Java_io_zer0_muse_terminal_Pty_setWindowSize(
        JNIEnv *env, jobject thiz, jint fd, jint rows, jint cols) {
    if (rows <= 0 || cols <= 0) return;
    struct winsize ws;
    ws.ws_row = (unsigned short) rows;
    ws.ws_col = (unsigned short) cols;
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;
    ioctl(fd, TIOCSWINSZ, &ws);
}

/** 关闭主端 fd(关闭后阻塞中的 read 会返回错误,用于终止会话)。 */
JNIEXPORT void JNICALL
Java_io_zer0_muse_terminal_Pty_closeFd(JNIEnv *env, jobject thiz, jint fd) {
    if (fd >= 0) close(fd);
}
