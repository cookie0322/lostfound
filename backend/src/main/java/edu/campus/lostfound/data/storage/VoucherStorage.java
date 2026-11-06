package edu.campus.lostfound.data.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 凭证文件存取（架构 表 3-2"数据访问：凭证文件存取"的落点）。
 *
 * <p>只存文件名，物理文件放在 {@code lf.voucher.dir} 下，文件名用 UUID，
 * 避免用用户上传的原始名直接落盘（路径穿越 + 重名覆盖）。
 * <p>对外访问必须经 Controller 鉴权后由 {@link #resolve(String)} 取流，
 * 不把 uploads 目录配成静态资源——否则任何人都能按文件名猜着下载（QR-04）。
 */
@Component
public class VoucherStorage {

    private static final Set<String> ALLOWED_EXT = Set.of(".jpg", ".jpeg", ".png", ".webp");

    private final Path root;

    public VoucherStorage(@Value("${lf.voucher.dir}") String dir) throws IOException {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    /** 保存并返回存进数据库的文件名 */
    public String save(MultipartFile file) throws IOException {
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.lastIndexOf('.') > 0) {
            ext = original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        }
        if (!ALLOWED_EXT.contains(ext)) {
            throw new IOException("只支持 jpg / png / webp 格式的凭证");
        }
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
        try (var in = file.getInputStream()) {
            Files.copy(in, root.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        }
        return fileName;
    }

    /** 解析并校验路径，防止 ../ 穿越 */
    public Path resolve(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        Path target = root.resolve(fileName).normalize();
        if (!target.startsWith(root) || !Files.exists(target)) {
            throw new IllegalArgumentException("凭证文件不存在");
        }
        return target;
    }
}
