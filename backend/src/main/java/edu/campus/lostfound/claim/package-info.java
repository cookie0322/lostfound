/**
 * F2 失物认领全流程管理（FR-06 ~ FR-12、FR-14）。
 *
 * <p>依赖约束（架构 3.3.3）：
 * <ul>
 *   <li>对 F1 只读（查询认证状态），不得反向依赖；</li>
 *   <li>状态只能经 {@code ClaimStateMachine} 迁移，业务代码不得直接写 state 字段；</li>
 *   <li>通知在事务提交后发出，不进入长事务。</li>
 * </ul>
 */
package edu.campus.lostfound.claim;
