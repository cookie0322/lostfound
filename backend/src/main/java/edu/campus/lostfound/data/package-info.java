/**
 * 数据访问模块：实体类、Mapper、凭证文件存取、可见性判断。
 *
 * <p>依赖约束（架构 3.3.3）：只依赖 MySQL 与本地文件目录，
 * 不依赖任何业务模块（auth / claim / notice / audit）。
 * <p>KV-03（凭证可见期）的判定统一收敛在 visibility 子包，不得散落在业务代码里。
 */
package edu.campus.lostfound.data;
