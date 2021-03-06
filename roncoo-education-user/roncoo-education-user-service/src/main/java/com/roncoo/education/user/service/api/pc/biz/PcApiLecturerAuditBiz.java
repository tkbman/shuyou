package com.roncoo.education.user.service.api.pc.biz;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import com.roncoo.education.user.common.req.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.roncoo.education.user.common.resq.LecturerAuditPageRESQ;
import com.roncoo.education.user.common.resq.LecturerAuditViewRESQ;
import com.roncoo.education.user.common.resq.LecturerExtViewRESQ;
import com.roncoo.education.user.service.dao.LecturerAuditDao;
import com.roncoo.education.user.service.dao.LecturerDao;
import com.roncoo.education.user.service.dao.LecturerExtDao;
import com.roncoo.education.user.service.dao.UserDao;
import com.roncoo.education.user.service.dao.UserExtDao;
import com.roncoo.education.user.service.dao.impl.mapper.entity.Lecturer;
import com.roncoo.education.user.service.dao.impl.mapper.entity.LecturerAudit;
import com.roncoo.education.user.service.dao.impl.mapper.entity.LecturerAuditExample;
import com.roncoo.education.user.service.dao.impl.mapper.entity.LecturerAuditExample.Criteria;
import com.roncoo.education.user.service.dao.impl.mapper.entity.LecturerExt;
import com.roncoo.education.user.service.dao.impl.mapper.entity.User;
import com.roncoo.education.user.service.dao.impl.mapper.entity.UserExt;
import com.roncoo.education.util.base.BaseBiz;
import com.roncoo.education.util.base.BaseException;
import com.roncoo.education.util.base.Page;
import com.roncoo.education.util.base.PageUtil;
import com.roncoo.education.util.base.Result;
import com.roncoo.education.util.enums.AuditStatusEnum;
import com.roncoo.education.util.enums.ResultEnum;
import com.roncoo.education.util.enums.UserTypeEnum;
import com.roncoo.education.util.tools.BeanUtil;
import com.roncoo.education.util.tools.NOUtil;
import com.roncoo.education.util.tools.SignUtil;
import com.roncoo.education.util.tools.StrUtil;
import com.xiaoleilu.hutool.crypto.DigestUtil;
import com.xiaoleilu.hutool.util.ObjectUtil;

@Component
public class PcApiLecturerAuditBiz extends BaseBiz {

    @Autowired
    private LecturerAuditDao lecturerAuditDao;
    @Autowired
    private LecturerDao lecturerDao;
    @Autowired
    private LecturerExtDao lecturerExtDao;
    @Autowired
    private UserDao userDao;
    @Autowired
    private UserExtDao userExtDao;

    public Result<Page<LecturerAuditPageRESQ>> listForPage(LecturerAuditPageREQ req) {
        LecturerAuditExample example = new LecturerAuditExample();
        Criteria c = example.createCriteria();
        if (StringUtils.hasText(req.getLecturerMobile())) {
            c.andLecturerMobileEqualTo(req.getLecturerMobile());
        }
        if (StringUtils.hasText(req.getLecturerName())) {
            c.andLecturerNameLike(PageUtil.rightLike(req.getLecturerName()));
        }
        if (req.getAuditStatus() != null) {
            c.andAuditStatusEqualTo(req.getAuditStatus());
        } else {
            c.andAuditStatusNotEqualTo(AuditStatusEnum.SUCCESS.getCode());
        }
        if (req.getStatusId() != null) {
            c.andStatusIdEqualTo(req.getStatusId());
        }
        example.setOrderByClause(" audit_status asc, status_id desc, sort desc, id desc ");
        Page<LecturerAudit> page = lecturerAuditDao.listForPage(req.getPageCurrent(), req.getPageSize(), example);
        return Result.success(PageUtil.transform(page, LecturerAuditPageRESQ.class));
    }

    /**
     * ????????????
     *
     * @param req
     * @return
     */
    @Transactional
    public Result<Integer> save(LecturerAuditSaveREQ req) {
        if (StringUtils.isEmpty(req.getLecturerMobile())) {
            return Result.error("?????????????????????");
        }
        // ?????????????????????
        String mobile = req.getLecturerMobile().trim();
        // ??????????????????
        if (!Pattern.compile(REGEX_MOBILE).matcher(mobile).matches()) {
            return Result.error("???????????????????????????");
        }
        // ???????????????????????????????????????(?????????????????????)
        UserExt userExt = userExtDao.getByMobile(mobile);
        // 1?????????????????????????????????
        if (ObjectUtil.isNull(userExt)) {
            if (StringUtils.isEmpty(req.getMobilePsw())) {
                return Result.error("??????????????????");
            }
            if (!req.getConfirmPasswd().equals(req.getMobilePsw())) {
                return Result.error("????????????????????????????????????");
            }
            // ????????????
            userExt = register(req, mobile);
        }

        // 2???????????????
        LecturerAudit lecturerAudit = lecturerAuditDao.getByLecturerUserNo(userExt.getUserNo());
        // ????????????????????????
        if (ObjectUtil.isNotNull(lecturerAudit)) {
            // ????????????
            if (AuditStatusEnum.SUCCESS.getCode().equals(lecturerAudit.getAuditStatus())) {
                // ????????????
                return Result.error(ResultEnum.LECTURER_REQUISITION_YET);
            } else if (AuditStatusEnum.WAIT.getCode().equals(lecturerAudit.getAuditStatus())) {
                // ?????????
                return Result.error(ResultEnum.LECTURER_REQUISITION_WAIT);
            } else {
                return Result.error(ResultEnum.LECTURER_REQUISITION_FAIL);
            }
        } else {
            // ???????????????
            int results = lecturerInfo(req, userExt);
            if (results < 0) {
                return Result.error(ResultEnum.USER_SAVE_FAIL);
            }
            return Result.success(results);
        }
    }

    /**
     * ??????????????????
     *
     * @param req
     * @return
     */
    public Result<Integer> update(LecturerAuditUpdateREQ req) {
        if (StringUtils.isEmpty(req.getId())) {
            return Result.error("ID????????????");
        }
        LecturerAudit lecturerAudit = lecturerAuditDao.getById(req.getId());
        if (ObjectUtil.isNull(lecturerAudit)) {
            return Result.error("?????????????????????");
        }
        LecturerAudit record = BeanUtil.copyProperties(req, LecturerAudit.class);
        record.setAuditStatus(AuditStatusEnum.WAIT.getCode());
        int results = lecturerAuditDao.updateById(record);
        if (results < 0) {
            return Result.error(ResultEnum.USER_UPDATE_FAIL);
        }
        return Result.success(results);
    }

    @Transactional
    public Result<Integer> audit(LecturerAuditAuditREQ req) {
        if (StringUtils.isEmpty(req.getId())) {
            return Result.error("ID????????????");
        }
        if (StringUtils.isEmpty(req.getAuditStatus())) {
            return Result.error("auditStatus????????????");
        }
        LecturerAudit lecturerAudit = lecturerAuditDao.getById(req.getId());
        if (ObjectUtil.isNull(lecturerAudit)) {
            return Result.error("?????????????????????");
        }
        if (AuditStatusEnum.SUCCESS.getCode().equals(req.getAuditStatus())) {
            // ?????????????????????????????????????????????
            Lecturer lecturer = lecturerDao.getByLecturerUserNo(lecturerAudit.getLecturerUserNo());
            if (ObjectUtil.isNull(lecturer)) {
                // ??????
                lecturer = BeanUtil.copyProperties(lecturerAudit, Lecturer.class);
                lecturer.setGmtCreate(null);
                lecturer.setGmtModified(null);
                lecturerDao.save(lecturer);
            } else {
                // ??????
                lecturer = BeanUtil.copyProperties(lecturerAudit, Lecturer.class);
                lecturer.setGmtCreate(null);
                lecturer.setGmtModified(null);
                lecturerDao.updateById(lecturer);
            }
            // ??????????????????????????????
            UserExt userExt = userExtDao.getByUserNo(lecturer.getLecturerUserNo());
            if (ObjectUtil.isNull(userExt)) {
                return Result.error("????????????????????????");
            }
            // ???????????????????????????
            userExt.setUserType(UserTypeEnum.LECTURER.getCode());
            userExtDao.updateById(userExt);
        }
        LecturerAudit record = BeanUtil.copyProperties(req, LecturerAudit.class);
        int results = lecturerAuditDao.updateById(record);
        if (results < 0) {
            return Result.error(ResultEnum.USER_LECTURER_AUDIT);
        }
        return Result.success(results);
    }

    public Result<LecturerAuditViewRESQ> view(LecturerAuditViewREQ req) {
        if (StringUtils.isEmpty(req.getId())) {
            return Result.error("ID????????????");
        }
        LecturerAudit record = lecturerAuditDao.getById(req.getId());
        if (ObjectUtil.isNull(record)) {
            return Result.error("?????????????????????");
        }
        LecturerAuditViewRESQ resq = BeanUtil.copyProperties(record, LecturerAuditViewRESQ.class);
        // ????????????????????????
        LecturerExt lecturerExt = lecturerExtDao.getByLecturerUserNo(resq.getLecturerUserNo());
        resq.setLecturerExt(BeanUtil.copyProperties(lecturerExt, LecturerExtViewRESQ.class));
        return Result.success(resq);
    }

    /**
     * ??????????????????
     */
    private UserExt register(LecturerAuditSaveREQ req, String mobile) {
        // ??????????????????
        User user = new User();
        user.setUserNo(NOUtil.getUserNo());
        user.setMobile(mobile);
        user.setMobileSalt(StrUtil.get32UUID());
        user.setMobilePsw(DigestUtil.sha1Hex(user.getMobileSalt() + req.getMobilePsw()));
        userDao.save(user);

        // ??????????????????
        UserExt userExt = new UserExt();
        userExt.setUserNo(user.getUserNo());
        userExt.setMobile(user.getMobile());
        userExt.setNickname(req.getLecturerName());
        userExtDao.save(userExt);
        return userExt;
    }

    /**
     * ??????????????????
     */
    private int lecturerInfo(LecturerAuditSaveREQ req, UserExt userExt) {
        // ??????????????????
        LecturerAudit infoAudit = BeanUtil.copyProperties(req, LecturerAudit.class);
        if (!StringUtils.isEmpty(userExt.getHeadImgUrl())) {
            infoAudit.setHeadImgUrl(userExt.getHeadImgUrl());
        }
        infoAudit.setLecturerUserNo(userExt.getUserNo());
        infoAudit.setLecturerProportion(LECTURER_DEFAULT_PROPORTION);// ?????????????????????????????????70
        int infoAuditNum = lecturerAuditDao.save(infoAudit);
        if (infoAuditNum < 1) {
            throw new BaseException("???????????????????????????");
        }

        // ??????????????????
        LecturerExt lecturerExt = new LecturerExt();
        lecturerExt.setLecturerUserNo(infoAudit.getLecturerUserNo());
        lecturerExt.setTotalIncome(BigDecimal.ZERO);
        lecturerExt.setHistoryMoney(BigDecimal.ZERO);
        lecturerExt.setEnableBalances(BigDecimal.ZERO);
        lecturerExt.setFreezeBalances(BigDecimal.ZERO);
        lecturerExt.setSign(SignUtil.getByLecturer(lecturerExt.getTotalIncome(), lecturerExt.getHistoryMoney(), lecturerExt.getEnableBalances(), lecturerExt.getFreezeBalances()));
        int lecturerExtNum = lecturerExtDao.save(lecturerExt);
        if (lecturerExtNum < 1) {
            throw new BaseException("???????????????????????????");
        }
        return lecturerExtNum;
    }

    public Result<Integer> check(LecturerAuditCheckMobileREQ req) {
        if (StringUtils.isEmpty(req.getLecturerMobile())) {
            return Result.error("?????????????????????");
        }
        // ?????????????????????
        String mobile = req.getLecturerMobile().trim();
        // ??????????????????
        if (!Pattern.compile(REGEX_MOBILE).matcher(mobile).matches()) {
            return Result.error("???????????????????????????");
        }
        // ???????????????????????????????????????(?????????????????????)
        UserExt userExt = userExtDao.getByMobile(mobile);
        // 1?????????????????????????????????
        if (ObjectUtil.isNull(userExt)) {
            return Result.success(501);
        }
        // 2???????????????
        LecturerAudit lecturerAudit = lecturerAuditDao.getByLecturerUserNo(userExt.getUserNo());
        // ????????????????????????
        if (ObjectUtil.isNotNull(lecturerAudit)) {
            // ????????????
            if (AuditStatusEnum.SUCCESS.getCode().equals(lecturerAudit.getAuditStatus())) {
                // ????????????
                return Result.success(503);
            } else if (AuditStatusEnum.WAIT.getCode().equals(lecturerAudit.getAuditStatus())) {
                // ?????????
                return Result.success(502);
            } else {
                return Result.success(506);
            }
        }
        // ???????????????
        return Result.success(1);
    }
}
