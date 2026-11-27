#define _GNU_SOURCE
#include <jni.h>
#include <link.h>
#include <elf.h>
#include <dlfcn.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <errno.h>
#include <stdatomic.h>
#include <android/log.h>

#define LOG_TAG "CyberDeckFdHook"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#if defined(__LP64__)
typedef Elf64_Dyn Elf_Dyn;
typedef Elf64_Sym Elf_Sym;
typedef Elf64_Rel Elf_Rel;
typedef Elf64_Rela Elf_Rela;
typedef Elf64_Phdr Elf_Phdr;
#define ELF_R_SYM(val) ELF64_R_SYM(val)
#define ELF_R_TYPE(val) ELF64_R_TYPE(val)
#if defined(__aarch64__)
#define REL_JUMP_SLOT R_AARCH64_JUMP_SLOT
#define REL_GLOB_DAT  R_AARCH64_GLOB_DAT
#elif defined(__x86_64__)
#define REL_JUMP_SLOT R_X86_64_JUMP_SLOT
#define REL_GLOB_DAT  R_X86_64_GLOB_DAT
#else
#define REL_JUMP_SLOT 0
#define REL_GLOB_DAT  0
#endif
#else
typedef Elf32_Dyn Elf_Dyn;
typedef Elf32_Sym Elf_Sym;
typedef Elf32_Rel Elf_Rel;
typedef Elf32_Rela Elf_Rela;
typedef Elf32_Phdr Elf_Phdr;
#define ELF_R_SYM(val) ELF32_R_SYM(val)
#define ELF_R_TYPE(val) ELF32_R_TYPE(val)
#if defined(__arm__)
#define REL_JUMP_SLOT R_ARM_JUMP_SLOT
#define REL_GLOB_DAT  R_ARM_GLOB_DAT
#elif defined(__i386__)
#define REL_JUMP_SLOT R_386_JMP_SLOT
#define REL_GLOB_DAT  R_386_GLOB_DAT
#else
#define REL_JUMP_SLOT 0
#define REL_GLOB_DAT  0
#endif
#endif

static atomic_int g_target_fd = -1;

static int parse_target_proc_fd(const char *path) {
    if (path == NULL) return -1;
    const char *prefix = "/proc/self/fd/";
    const size_t prefix_len = 14;
    if (strncmp(path, prefix, prefix_len) != 0) return -1;
    const char *p = path + prefix_len;
    if (*p == '\0') return -1;
    char *endptr = NULL;
    long val = strtol(p, &endptr, 10);
    if (endptr == p || *endptr != '\0' || val < 0 || val > 1000000L) return -1;
    int target = atomic_load(&g_target_fd);
    if (target >= 0 && (int)val == target) {
        return (int)val;
    }
    return -1;
}

static int dup_reset_fd(int raw_fd, int cloexec) {
    int new_fd = cloexec ? fcntl(raw_fd, F_DUPFD_CLOEXEC, 0) : dup(raw_fd);
    if (new_fd >= 0) {
        lseek(new_fd, 0, SEEK_SET);
    }
    return new_fd;
}

static FILE *hook_fopen(const char *pathname, const char *mode) {
    int fd = parse_target_proc_fd(pathname);
    if (fd >= 0) {
        int cloexec = (mode != NULL && strchr(mode, 'e') != NULL);
        int duped = dup_reset_fd(fd, cloexec);
        if (duped >= 0) {
            FILE *fp = fdopen(duped, mode != NULL ? mode : "rb");
            if (fp != NULL) {
                return fp;
            }
            close(duped);
        }
    }
    return fopen(pathname, mode);
}

static int hook_open(const char *pathname, int flags, ...) {
    mode_t mode = 0;
    if ((flags & O_CREAT) != 0) {
        va_list ap;
        va_start(ap, flags);
        mode = (mode_t)va_arg(ap, int);
        va_end(ap);
    }
    int fd = parse_target_proc_fd(pathname);
    if (fd >= 0) {
        return dup_reset_fd(fd, (flags & O_CLOEXEC) != 0);
    }
    if ((flags & O_CREAT) != 0) {
        return open(pathname, flags, mode);
    }
    return open(pathname, flags);
}

static int hook_open_2(const char *pathname, int flags) {
    int fd = parse_target_proc_fd(pathname);
    if (fd >= 0) {
        return dup_reset_fd(fd, (flags & O_CLOEXEC) != 0);
    }
    return open(pathname, flags);
}

static int hook_openat(int dirfd, const char *pathname, int flags, ...) {
    mode_t mode = 0;
    if ((flags & O_CREAT) != 0) {
        va_list ap;
        va_start(ap, flags);
        mode = (mode_t)va_arg(ap, int);
        va_end(ap);
    }
    int fd = parse_target_proc_fd(pathname);
    if (fd >= 0) {
        return dup_reset_fd(fd, (flags & O_CLOEXEC) != 0);
    }
    if ((flags & O_CREAT) != 0) {
        return openat(dirfd, pathname, flags, mode);
    }
    return openat(dirfd, pathname, flags);
}

static int hook_openat_2(int dirfd, const char *pathname, int flags) {
    int fd = parse_target_proc_fd(pathname);
    if (fd >= 0) {
        return dup_reset_fd(fd, (flags & O_CLOEXEC) != 0);
    }
    return openat(dirfd, pathname, flags);
}

static int hook_stat(const char *pathname, struct stat *buf) {
    int fd = parse_target_proc_fd(pathname);
    if (fd >= 0 && buf != NULL) {
        return fstat(fd, buf);
    }
    return stat(pathname, buf);
}

static int hook_lstat(const char *pathname, struct stat *buf) {
    int fd = parse_target_proc_fd(pathname);
    if (fd >= 0 && buf != NULL) {
        return fstat(fd, buf);
    }
    return lstat(pathname, buf);
}

static int hook_fstatat(int dirfd, const char *pathname, struct stat *buf, int flags) {
    int fd = parse_target_proc_fd(pathname);
    if (fd >= 0 && buf != NULL) {
        return fstat(fd, buf);
    }
    return fstatat(dirfd, pathname, buf, flags);
}

static int hook_access(const char *pathname, int mode) {
    int fd = parse_target_proc_fd(pathname);
    if (fd >= 0) {
        if ((mode & W_OK) != 0) {
            errno = EACCES;
            return -1;
        }
        return 0;
    }
    return access(pathname, mode);
}

static int hook_faccessat(int dirfd, const char *pathname, int mode, int flags) {
    int fd = parse_target_proc_fd(pathname);
    if (fd >= 0) {
        if ((mode & W_OK) != 0) {
            errno = EACCES;
            return -1;
        }
        return 0;
    }
    return faccessat(dirfd, pathname, mode, flags);
}

static void *resolve_hook_for_symbol(const char *name) {
    if (name == NULL) return NULL;
    if (strcmp(name, "fopen") == 0 || strcmp(name, "fopen64") == 0) {
        return (void *)hook_fopen;
    }
    if (strcmp(name, "open") == 0 || strcmp(name, "open64") == 0) {
        return (void *)hook_open;
    }
    if (strcmp(name, "__open_2") == 0) {
        return (void *)hook_open_2;
    }
    if (strcmp(name, "openat") == 0 || strcmp(name, "openat64") == 0) {
        return (void *)hook_openat;
    }
    if (strcmp(name, "__openat_2") == 0) {
        return (void *)hook_openat_2;
    }
    if (strcmp(name, "stat") == 0 || strcmp(name, "stat64") == 0) {
        return (void *)hook_stat;
    }
    if (strcmp(name, "lstat") == 0 || strcmp(name, "lstat64") == 0) {
        return (void *)hook_lstat;
    }
    if (strcmp(name, "fstatat") == 0 || strcmp(name, "fstatat64") == 0) {
        return (void *)hook_fstatat;
    }
    if (strcmp(name, "access") == 0) {
        return (void *)hook_access;
    }
    if (strcmp(name, "faccessat") == 0) {
        return (void *)hook_faccessat;
    }
    return NULL;
}

static int patch_got_slot(uintptr_t *slot, void *replacement) {
    if (slot == NULL || replacement == NULL) return 0;
    if (*slot == (uintptr_t)replacement) return 0;
    long page_size = sysconf(_SC_PAGESIZE);
    if (page_size <= 0) page_size = 4096;
    uintptr_t page_mask = (uintptr_t)(page_size - 1);
    uintptr_t start = ((uintptr_t)slot) & ~page_mask;
    uintptr_t end = (((uintptr_t)slot) + sizeof(uintptr_t) + page_mask) & ~page_mask;
    if (mprotect((void *)start, (size_t)(end - start), PROT_READ | PROT_WRITE) != 0) {
        return 0;
    }
    *slot = (uintptr_t)replacement;
    atomic_thread_fence(memory_order_seq_cst);
    return 1;
}

static int is_target_library(const char *name) {
    if (name == NULL || name[0] == '\0') return 0;
    if (strstr(name, "cyberdeck_fdhook") != NULL || strstr(name, "zstd") != NULL) return 0;
    if (strncmp(name, "/system/", 8) == 0 || strncmp(name, "/apex/", 6) == 0 ||
        strncmp(name, "/vendor/", 8) == 0 || strncmp(name, "/odm/", 5) == 0 ||
        strstr(name, "/libc.so") != NULL || strstr(name, "/libdl.so") != NULL ||
        strstr(name, "/linker") != NULL || strstr(name, "/libart.so") != NULL) {
        return 0;
    }
    return (strstr(name, "llama") != NULL ||
            strstr(name, "ggml") != NULL ||
            strstr(name, "c++_shared") != NULL ||
            strstr(name, "com.offlinecyberdeck") != NULL ||
            strstr(name, "/data/app/") != NULL);
}

typedef struct {
    int patched_libs;
    int patched_slots;
} HookStats;

static uintptr_t normalize_dyn_ptr(uintptr_t bias, uintptr_t val) {
    if (val == 0) return 0;
    return (val < bias) ? (bias + val) : val;
}

static int dl_callback(struct dl_phdr_info *info, size_t size, void *data) {
    (void)size;
    HookStats *stats = (HookStats *)data;
    if (!is_target_library(info->dlpi_name)) return 0;

    const Elf_Dyn *dyn = NULL;
    uintptr_t min_vaddr = (uintptr_t)-1;
    uintptr_t max_vaddr = 0;

    for (ElfW(Half) i = 0; i < info->dlpi_phnum; i++) {
        const Elf_Phdr *ph = &info->dlpi_phdr[i];
        if (ph->p_type == PT_LOAD) {
            if (ph->p_vaddr < min_vaddr) min_vaddr = ph->p_vaddr;
            if (ph->p_vaddr + ph->p_memsz > max_vaddr) max_vaddr = ph->p_vaddr + ph->p_memsz;
        } else if (ph->p_type == PT_DYNAMIC) {
            dyn = (const Elf_Dyn *)(info->dlpi_addr + ph->p_vaddr);
        }
    }
    if (dyn == NULL || min_vaddr >= max_vaddr) return 0;

    uintptr_t map_start = info->dlpi_addr + min_vaddr;
    uintptr_t map_end = info->dlpi_addr + max_vaddr;

    const char *strtab = NULL;
    const Elf_Sym *symtab = NULL;
    size_t strsz = 0;
    uintptr_t jmprel = 0;
    size_t pltrelsz = 0;
    int pltrel_type = DT_RELA;
    uintptr_t rela = 0;
    size_t relasz = 0;
    uintptr_t rel = 0;
    size_t relsz = 0;

    for (const Elf_Dyn *d = dyn; d->d_tag != DT_NULL; d++) {
        switch (d->d_tag) {
            case DT_STRTAB:
                strtab = (const char *)normalize_dyn_ptr(info->dlpi_addr, (uintptr_t)d->d_un.d_ptr);
                break;
            case DT_STRSZ:
                strsz = (size_t)d->d_un.d_val;
                break;
            case DT_SYMTAB:
                symtab = (const Elf_Sym *)normalize_dyn_ptr(info->dlpi_addr, (uintptr_t)d->d_un.d_ptr);
                break;
            case DT_JMPREL:
                jmprel = normalize_dyn_ptr(info->dlpi_addr, (uintptr_t)d->d_un.d_ptr);
                break;
            case DT_PLTRELSZ:
                pltrelsz = (size_t)d->d_un.d_val;
                break;
            case DT_PLTREL:
                pltrel_type = (int)d->d_un.d_val;
                break;
            case DT_RELA:
                rela = normalize_dyn_ptr(info->dlpi_addr, (uintptr_t)d->d_un.d_ptr);
                break;
            case DT_RELASZ:
                relasz = (size_t)d->d_un.d_val;
                break;
            case DT_REL:
                rel = normalize_dyn_ptr(info->dlpi_addr, (uintptr_t)d->d_un.d_ptr);
                break;
            case DT_RELSZ:
                relsz = (size_t)d->d_un.d_val;
                break;
            default:
                break;
        }
    }

    if (strtab == NULL || symtab == NULL) return 0;

    int lib_slots = 0;

    // 1. Scan .rela.plt / .rel.plt
    if (jmprel != 0 && pltrelsz > 0) {
        if (pltrel_type == DT_RELA) {
            size_t count = pltrelsz / sizeof(Elf_Rela);
            const Elf_Rela *entries = (const Elf_Rela *)jmprel;
            for (size_t i = 0; i < count; i++) {
                uintptr_t type = (uintptr_t)ELF_R_TYPE(entries[i].r_info);
                if (type != REL_JUMP_SLOT && type != REL_GLOB_DAT) continue;
                uintptr_t sym_idx = (uintptr_t)ELF_R_SYM(entries[i].r_info);
                const Elf_Sym *sym = &symtab[sym_idx];
                if (strsz > 0 && sym->st_name >= strsz) continue;
                void *hook = resolve_hook_for_symbol(strtab + sym->st_name);
                if (hook == NULL) continue;
                uintptr_t slot_addr = info->dlpi_addr + entries[i].r_offset;
                if (slot_addr >= map_start && slot_addr + sizeof(uintptr_t) <= map_end) {
                    lib_slots += patch_got_slot((uintptr_t *)slot_addr, hook);
                }
            }
        } else if (pltrel_type == DT_REL) {
            size_t count = pltrelsz / sizeof(Elf_Rel);
            const Elf_Rel *entries = (const Elf_Rel *)jmprel;
            for (size_t i = 0; i < count; i++) {
                uintptr_t type = (uintptr_t)ELF_R_TYPE(entries[i].r_info);
                if (type != REL_JUMP_SLOT && type != REL_GLOB_DAT) continue;
                uintptr_t sym_idx = (uintptr_t)ELF_R_SYM(entries[i].r_info);
                const Elf_Sym *sym = &symtab[sym_idx];
                if (strsz > 0 && sym->st_name >= strsz) continue;
                void *hook = resolve_hook_for_symbol(strtab + sym->st_name);
                if (hook == NULL) continue;
                uintptr_t slot_addr = info->dlpi_addr + entries[i].r_offset;
                if (slot_addr >= map_start && slot_addr + sizeof(uintptr_t) <= map_end) {
                    lib_slots += patch_got_slot((uintptr_t *)slot_addr, hook);
                }
            }
        }
    }

    // 2. Scan .rela.dyn / .rel.dyn for BIND_NOW GLOB_DAT / JUMP_SLOT entries
    if (rela != 0 && relasz > 0) {
        size_t count = relasz / sizeof(Elf_Rela);
        const Elf_Rela *entries = (const Elf_Rela *)rela;
        for (size_t i = 0; i < count; i++) {
            uintptr_t type = (uintptr_t)ELF_R_TYPE(entries[i].r_info);
            if (type != REL_JUMP_SLOT && type != REL_GLOB_DAT) continue;
            uintptr_t sym_idx = (uintptr_t)ELF_R_SYM(entries[i].r_info);
            const Elf_Sym *sym = &symtab[sym_idx];
            if (strsz > 0 && sym->st_name >= strsz) continue;
            void *hook = resolve_hook_for_symbol(strtab + sym->st_name);
            if (hook == NULL) continue;
            uintptr_t slot_addr = info->dlpi_addr + entries[i].r_offset;
            if (slot_addr >= map_start && slot_addr + sizeof(uintptr_t) <= map_end) {
                lib_slots += patch_got_slot((uintptr_t *)slot_addr, hook);
            }
        }
    }
    if (rel != 0 && relsz > 0) {
        size_t count = relsz / sizeof(Elf_Rel);
        const Elf_Rel *entries = (const Elf_Rel *)rel;
        for (size_t i = 0; i < count; i++) {
            uintptr_t type = (uintptr_t)ELF_R_TYPE(entries[i].r_info);
            if (type != REL_JUMP_SLOT && type != REL_GLOB_DAT) continue;
            uintptr_t sym_idx = (uintptr_t)ELF_R_SYM(entries[i].r_info);
            const Elf_Sym *sym = &symtab[sym_idx];
            if (strsz > 0 && sym->st_name >= strsz) continue;
            void *hook = resolve_hook_for_symbol(strtab + sym->st_name);
            if (hook == NULL) continue;
            uintptr_t slot_addr = info->dlpi_addr + entries[i].r_offset;
            if (slot_addr >= map_start && slot_addr + sizeof(uintptr_t) <= map_end) {
                lib_slots += patch_got_slot((uintptr_t *)slot_addr, hook);
            }
        }
    }

    if (lib_slots > 0) {
        stats->patched_libs++;
        stats->patched_slots += lib_slots;
        LOGI("Patched %d GOT slots in %s", lib_slots, info->dlpi_name);
    }
    return 0;
}

JNIEXPORT jstring JNICALL
Java_com_offlinecyberdeck_ai_FdHookBridge_nativeInstallHooks(JNIEnv *env, jclass clazz) {
    (void)clazz;
    HookStats stats = {0, 0};
    dl_iterate_phdr(dl_callback, &stats);
    char buf[96];
    snprintf(buf, sizeof(buf), "libs=%d,slots=%d", stats.patched_libs, stats.patched_slots);
    return (*env)->NewStringUTF(env, buf);
}

JNIEXPORT void JNICALL
Java_com_offlinecyberdeck_ai_FdHookBridge_nativeSetTargetFd(JNIEnv *env, jclass clazz, jint fd) {
    (void)env;
    (void)clazz;
    atomic_store(&g_target_fd, (int)fd);
}
