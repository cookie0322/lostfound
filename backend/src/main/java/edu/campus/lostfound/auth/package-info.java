/**
 * F1 统一身份认证与账号绑定（FR-01 ~ FR-05、FR-13）。
 *
 * <p>依赖约束（架构 3.3.3）：
 * <ul>
 *   <li>可依赖 data / notice / audit；</li>
 *   <li>不得依赖 claim（F1 不得反向依赖 F2）；</li>
 *   <li>对 claim 只暴露"查询认证状态"的只读接口。</li>
 * </ul>
 */
package edu.campus.lostfound.auth;
