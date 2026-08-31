package com.weatherhub.rbac; // 声明当前 Java 文件所属的包路径

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper; // 导入 LambdaQueryWrapper 类型或包供本文件使用
import com.baomidou.mybatisplus.core.toolkit.StringUtils; // 导入 StringUtils 类型或包供本文件使用
import com.weatherhub.common.BusinessException; // 导入 BusinessException 类型或包供本文件使用
import com.weatherhub.rbac.dto.MenuVO; // 导入 MenuVO 类型或包供本文件使用
import com.weatherhub.rbac.dto.SaveMenuRequest; // 导入 SaveMenuRequest 类型或包供本文件使用
import lombok.RequiredArgsConstructor; // 导入 RequiredArgsConstructor 类型或包供本文件使用
import org.springframework.stereotype.Service; // 导入 Service 类型或包供本文件使用
import org.springframework.transaction.annotation.Transactional; // 导入 Transactional 类型或包供本文件使用

import java.util.ArrayList; // 导入 ArrayList 类型或包供本文件使用
import java.util.List; // 导入 List 类型或包供本文件使用
import java.util.Map; // 导入 Map 类型或包供本文件使用
import java.util.Objects; // 导入 Objects 类型或包供本文件使用
import java.util.stream.Collectors; // 导入 Collectors 类型或包供本文件使用

@Service // 声明这是 Spring 管理的业务服务组件
@RequiredArgsConstructor // 让 Lombok 为 final 字段生成构造方法
public class MenuService { // 声明 MenuService 类

    private final MenuMapper menuMapper; // 定义 menuMapper 字段保存对象状态或依赖
    private final RoleMenuMapper roleMenuMapper; // 定义 roleMenuMapper 字段保存对象状态或依赖

    public List<MenuVO> tree() { // 定义 tree 方法的入口
        List<Menu> menus = menuMapper.selectList(new LambdaQueryWrapper<Menu>().orderByAsc(Menu::getSortNo).orderByAsc(Menu::getId)); // 按排序查出全部菜单
        return toTree(menus); // 组装成树后返回
    } // 

    public List<MenuVO> sidebarOf(Long userId) { // 定义 sidebarOf 方法的入口
        List<Menu> menus = menuMapper.selectByUserId(userId).stream() // 查出当前用户已授权菜单
                .filter(menu -> Menu.TYPE_MENU.equals(menu.getType()) || Menu.TYPE_DIR.equals(menu.getType())) // 侧栏只展示目录和页面
                .toList(); // 收集成列表
        return toTree(menus); // 组装成树后返回
    } // 

    public List<String> permissionCodesOf(Long userId) { // 定义 permissionCodesOf 方法的入口
        return menuMapper.selectCodesByUserId(userId); // 返回该用户菜单上的权限编码
    } // 

    @Transactional // 让当前方法在事务中执行
    public MenuVO create(SaveMenuRequest request) { // 定义 create 方法的入口
        Menu menu = new Menu(); // 创建新的菜单对象
        fill(menu, request); // 把请求字段写入菜单
        menuMapper.insert(menu); // 插入菜单记录
        return MenuVO.from(menu); // 返回刚创建的菜单
    } // 

    @Transactional // 让当前方法在事务中执行
    public MenuVO update(Long id, SaveMenuRequest request) { // 定义 update 方法的入口
        Menu menu = requireMenu(id); // 确认菜单存在
        Long parentId = normalizeParentId(request.parentId()); // 规范化父级主键
        if (Objects.equals(id, parentId)) { // 禁止把菜单挂到自己下面
            throw new BusinessException("不能把菜单设置为自己的子菜单"); // 抛出业务异常
        } // 
        if (isDescendant(id, parentId)) { // 禁止把菜单挂到自己的子孙下面
            throw new BusinessException("不能把菜单移动到自己的子菜单下"); // 抛出业务异常
        } // 
        fill(menu, request); // 把请求字段写入菜单
        menuMapper.updateById(menu); // 保存菜单变更
        return MenuVO.from(menu); // 返回更新后的菜单
    } // 

    @Transactional // 让当前方法在事务中执行
    public void delete(Long id) { // 定义 delete 方法的入口
        requireMenu(id); // 确认菜单存在
        Long childCount = menuMapper.selectCount(new LambdaQueryWrapper<Menu>().eq(Menu::getParentId, id)); // 统计子菜单数量
        if (childCount > 0) { // 有子菜单时不允许删除
            throw new BusinessException("请先删除子菜单"); // 抛出业务异常
        } // 
        roleMenuMapper.deleteByMenuId(id); // 先删除角色菜单关系
        menuMapper.deleteById(id); // 再删除菜单记录
    } // 

    public long countAll() { // 定义 countAll 方法的入口
        return menuMapper.selectCount(null); // 返回菜单总数
    } // 

    private void fill(Menu menu, SaveMenuRequest request) { // 定义 fill 方法的入口
        menu.setParentId(normalizeParentId(request.parentId())); // 写入父级
        menu.setName(request.name().trim()); // 写入名称
        menu.setPath(blankToNull(request.path())); // 写入路由
        menu.setIcon(blankToNull(request.icon())); // 写入图标
        menu.setSortNo(request.sortNo() == null ? 0 : request.sortNo()); // 未传排序时默认为 0
        menu.setPermissionCode(blankToNull(request.permissionCode())); // 写入权限编码
        menu.setType(request.type()); // 写入类型
        menu.setStatus(StringUtils.isBlank(request.status()) ? Menu.STATUS_ENABLED : request.status()); // 未传状态时默认启用
    } // 

    private Menu requireMenu(Long id) { // 定义 requireMenu 方法的入口
        Menu menu = menuMapper.selectById(id); // 按主键查询菜单
        if (menu == null) { // 判断菜单是否不存在
            throw new BusinessException(404, "菜单不存在"); // 抛出业务异常
        } // 
        return menu; // 返回查到的菜单
    } // 

    private boolean isDescendant(Long ancestorId, Long maybeChildId) { // 定义 isDescendant 方法的入口
        Long current = maybeChildId; // 从候选父级开始向上走
        int guard = 0; // 防止脏数据造成死循环
        while (current != null && current != 0 && guard++ < 32) { // 尚未走到根节点时继续
            if (Objects.equals(current, ancestorId)) { // 向上走到了自己，说明形成了环
                return true; // 判定为子孙节点
            } // 
            Menu parent = menuMapper.selectById(current); // 查询当前节点
            current = parent == null ? 0L : parent.getParentId(); // 继续向上找父级
        } // 
        return false; // 不是子孙节点
    } // 

    private List<MenuVO> toTree(List<Menu> menus) { // 定义 toTree 方法的入口
        List<MenuVO> nodes = menus.stream().map(MenuVO::from).toList(); // 先转成可挂子节点的 VO
        Map<Long, MenuVO> byId = nodes.stream().collect(Collectors.toMap(MenuVO::id, node -> node)); // 按主键建立索引
        List<MenuVO> roots = new ArrayList<>(); // 存放顶级节点
        for (MenuVO node : nodes) { // 遍历全部节点
            Long parentId = node.parentId() == null ? 0L : node.parentId(); // 空父级按顶级处理
            if (parentId == 0 || !byId.containsKey(parentId)) { // 没有父级或不在当前集合中
                roots.add(node); // 作为根节点
            } else { // 开始当前声明或控制结构的代码块
                byId.get(parentId).children().add(node); // 挂到父节点下面
            } // 
        } // 
        return roots; // 返回树
    } // 

    private Long normalizeParentId(Long parentId) { // 定义 normalizeParentId 方法的入口
        return parentId == null ? 0L : parentId; // 空值按顶级处理
    } // 

    private String blankToNull(String value) { // 定义 blankToNull 方法的入口
        return StringUtils.isBlank(value) ? null : value.trim(); // 空白字符串转成空值
    } // 
} // 
