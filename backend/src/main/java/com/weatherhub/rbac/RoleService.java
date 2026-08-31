package com.weatherhub.rbac; // 声明当前 Java 文件所属的包路径

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // 导入 LambdaQueryWrapper 类型或包供本文件使用
import com.weatherhub.common.BusinessException; // 导入 BusinessException 类型或包供本文件使用
import com.weatherhub.rbac.dto.RoleVO; // 导入 RoleVO 类型或包供本文件使用
import com.weatherhub.rbac.dto.SaveRoleRequest; // 导入 SaveRoleRequest 类型或包供本文件使用
import lombok.RequiredArgsConstructor; // 导入 RequiredArgsConstructor 类型或包供本文件使用
import org.springframework.stereotype.Service; // 导入 Service 类型或包供本文件使用
import org.springframework.transaction.annotation.Transactional; // 导入 Transactional 类型或包供本文件使用

import java.util.List; // 导入 List 类型或包供本文件使用

@Service // 声明这是 Spring 管理的业务服务组件
@RequiredArgsConstructor // 让 Lombok 为 final 字段生成构造方法
public class RoleService { // 声明 RoleService 类

    public static final String ADMIN_CODE = "ADMIN"; // 内置管理员角色编码，不允许删除

    private final RoleMapper roleMapper; // 定义 roleMapper 字段保存对象状态或依赖
    private final RoleMenuMapper roleMenuMapper; // 定义 roleMenuMapper 字段保存对象状态或依赖
    private final UserRoleMapper userRoleMapper; // 定义 userRoleMapper 字段保存对象状态或依赖

    public List<RoleVO> list() { // 定义 list 方法的入口
        return roleMapper.selectList(new LambdaQueryWrapper<Role>().orderByAsc(Role::getId)).stream() // 按主键查出全部角色
                .map(this::toVO) // 附带已绑定菜单
                .toList(); // 收集成列表
    } // 

    public RoleVO get(Long id) { // 定义 get 方法的入口
        return toVO(requireRole(id)); // 查询角色并附带菜单
    } // 

    @Transactional // 让当前方法在事务中执行
    public RoleVO create(SaveRoleRequest request) { // 定义 create 方法的入口
        String code = request.code().trim(); // 去掉角色编码两端空格
        if (existsCode(code, null)) { // 判断编码是否已被占用
            throw new BusinessException("角色编码已存在"); // 抛出业务异常
        } // 
        Role role = new Role(); // 创建新的角色对象
        role.setCode(code); // 写入编码
        role.setName(request.name().trim()); // 写入名称
        roleMapper.insert(role); // 插入角色记录
        bindMenus(role.getId(), request.menuIds()); // 绑定菜单
        return toVO(role); // 返回带菜单的角色
    } // 

    @Transactional // 让当前方法在事务中执行
    public RoleVO update(Long id, SaveRoleRequest request) { // 定义 update 方法的入口
        Role role = requireRole(id); // 确认角色存在
        role.setName(request.name().trim()); // 更新显示名，编码不允许修改
        roleMapper.updateById(role); // 保存角色变更
        bindMenus(id, request.menuIds()); // 用新的菜单列表覆盖旧关系
        return toVO(role); // 返回带菜单的角色
    } // 

    @Transactional // 让当前方法在事务中执行
    public void delete(Long id) { // 定义 delete 方法的入口
        Role role = requireRole(id); // 确认角色存在
        if (ADMIN_CODE.equals(role.getCode())) { // 禁止删除内置管理员角色
            throw new BusinessException("不能删除管理员角色"); // 抛出业务异常
        } // 
        Long used = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, id)); // 统计仍在使用该角色的用户
        if (used > 0) { // 还有用户占用时不允许删除
            throw new BusinessException("仍有用户使用该角色，无法删除"); // 抛出业务异常
        } // 
        roleMenuMapper.deleteByRoleId(id); // 先删除角色菜单关系
        roleMapper.deleteById(id); // 再删除角色记录
    } // 

    public long countAll() { // 定义 countAll 方法的入口
        return roleMapper.selectCount(null); // 返回角色总数
    } // 

    private RoleVO toVO(Role role) { // 定义 toVO 方法的入口
        return RoleVO.from(role, roleMenuMapper.selectMenuIdsByRoleId(role.getId())); // 查询已绑定菜单后组装 VO
    } // 

    private void bindMenus(Long roleId, List<Long> menuIds) { // 定义 bindMenus 方法的入口
        roleMenuMapper.deleteByRoleId(roleId); // 先清空旧关系
        if (menuIds == null) { // 未传菜单时只清空
            return; // 结束绑定
        } // 
        for (Long menuId : menuIds) { // 遍历要绑定的菜单
            if (menuId != null) { // 跳过空主键
                roleMenuMapper.insertIgnore(roleId, menuId); // 写入角色菜单关系
            } // 
        } // 
    } // 

    private Role requireRole(Long id) { // 定义 requireRole 方法的入口
        Role role = roleMapper.selectById(id); // 按主键查询角色
        if (role == null) { // 判断角色是否不存在
            throw new BusinessException(404, "角色不存在"); // 抛出业务异常
        } // 
        return role; // 返回查到的角色
    } // 

    private boolean existsCode(String code, Long excludeId) { // 定义 existsCode 方法的入口
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<Role>() // 构造编码查重条件
                .eq(Role::getCode, code) // 按编码精确匹配
                .ne(excludeId != null, Role::getId, excludeId); // 编辑时排除当前角色自己
        return roleMapper.selectCount(wrapper) > 0; // 存在同名编码则返回 true
    } // 
} // 
