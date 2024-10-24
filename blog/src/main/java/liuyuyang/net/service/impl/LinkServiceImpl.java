package liuyuyang.net.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import liuyuyang.net.execption.CustomException;
import liuyuyang.net.mapper.LinkMapper;
import liuyuyang.net.mapper.LinkTypeMapper;
import liuyuyang.net.model.Link;
import liuyuyang.net.service.LinkService;
import liuyuyang.net.utils.EmailUtils;
import liuyuyang.net.utils.YuYangUtils;
import liuyuyang.net.vo.PageVo;
import liuyuyang.net.vo.link.LinkFilterVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class LinkServiceImpl extends ServiceImpl<LinkMapper, Link> implements LinkService {
    @Resource
    private YuYangUtils yuYangUtils;
    @Resource
    private LinkMapper linkMapper;
    @Resource
    private LinkTypeMapper linkTypeMapper;
    @Resource
    private EmailUtils emailUtils;
    @Value("${spring.mail.username}")
    private String from;

    @Override
    public void add(Link link, String token) throws Exception {
        // 前端用户手动提交
        if (token == null || token.isEmpty()) {
            // 添加之前先判断所选的网站类型是否为当前用户可选的
            Integer isAdmin = linkTypeMapper.selectById(link.getTypeId()).getIsAdmin();
            if (isAdmin == 1) throw new CustomException(400, "该类型需要管理员权限才能添加");
            linkMapper.insert(link);

            // 邮件提醒
            emailUtils.send(from, "您有新的友联等待审核", link.toString());

            return;
        }

        // 判断权限
        Boolean isAdminPermissions = yuYangUtils.isAdmin(token);
        // 如果是超级管理员在添加时候不需要审核，直接显示
        if (isAdminPermissions) {
            link.setAuditStatus(1);
            linkMapper.insert(link);
        }
    }

    @Override
    public Link get(Integer id) {
        Link data = linkMapper.selectById(id);

        if (data == null) {
            throw new CustomException(400, "该网站不存在");
        }

        // 获取网站类型
        data.setType(linkTypeMapper.selectById(id));

        return data;
    }

    @Override
    public List<Link> list(LinkFilterVo filterVo) {
        QueryWrapper<Link> queryWrapper = yuYangUtils.queryWrapperFilter(filterVo);
        queryWrapper.eq("audit_status", filterVo.getStatus()); // 只显示审核成功的网站

        // 查询所有网站
        List<Link> list = linkMapper.selectList(queryWrapper);

        if (!list.isEmpty()) {
            for (Link link : list) {
                link.setType(linkTypeMapper.selectById(link.getTypeId()));
            }
        }

        list = list.stream().sorted((o1, o2) -> o2.getCreateTime().compareTo(o1.getCreateTime())).collect(Collectors.toList());

        return list;
    }

    @Override
    public Page<Link> paging(LinkFilterVo filterVo, PageVo pageVo) {
        List<Link> list = list(filterVo);

        // 分页处理
        int start = (pageVo.getPage() - 1) * pageVo.getSize();
        int end = Math.min(start + pageVo.getSize(), list.size());
        List<Link> pagedLinks = list.subList(start, end);

        // 返回分页结果
        Page<Link> result = new Page<>(pageVo.getPage(), pageVo.getSize());
        result.setRecords(pagedLinks);
        result.setTotal(list.size());

        return result;
    }
}