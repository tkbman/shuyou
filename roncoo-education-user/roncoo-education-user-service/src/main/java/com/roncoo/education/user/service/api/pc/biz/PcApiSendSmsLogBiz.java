package com.roncoo.education.user.service.api.pc.biz;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.roncoo.education.system.feign.interfaces.IFeignSys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.aliyuncs.exceptions.ClientException;
import com.roncoo.education.system.feign.vo.SysVO;
import com.roncoo.education.user.common.req.SendSmsLogPageREQ;
import com.roncoo.education.user.common.req.SendSmsLogSendREQ;
import com.roncoo.education.user.common.resq.SendSmsLogPageRESQ;
import com.roncoo.education.user.service.dao.PlatformDao;
import com.roncoo.education.user.service.dao.SendSmsLogDao;
import com.roncoo.education.user.service.dao.UserDao;
import com.roncoo.education.user.service.dao.impl.mapper.entity.Platform;
import com.roncoo.education.user.service.dao.impl.mapper.entity.SendSmsLog;
import com.roncoo.education.user.service.dao.impl.mapper.entity.SendSmsLogExample;
import com.roncoo.education.user.service.dao.impl.mapper.entity.SendSmsLogExample.Criteria;
import com.roncoo.education.user.service.dao.impl.mapper.entity.User;
import com.roncoo.education.util.aliyun.Aliyun;
import com.roncoo.education.util.aliyun.AliyunUtil;
import com.roncoo.education.util.base.BaseBiz;
import com.roncoo.education.util.base.Page;
import com.roncoo.education.util.base.PageUtil;
import com.roncoo.education.util.base.Result;
import com.roncoo.education.util.enums.IsSuccessEnum;
import com.roncoo.education.util.enums.ResultEnum;
import com.roncoo.education.util.enums.StatusIdEnum;
import com.roncoo.education.util.tools.BeanUtil;
import com.roncoo.education.util.tools.DateUtil;
import com.xiaoleilu.hutool.util.ObjectUtil;
import com.xiaoleilu.hutool.util.RandomUtil;

@Component
public class PcApiSendSmsLogBiz extends BaseBiz {

	private static final String REGEX_MOBILE = "^((13[0-9])|(14[5,7,9])|(15[0-3,5-9])|(17[0,3,5-8])|(18[0-9])|166|198|199)\\d{8}$";// ??????????????????

	@Autowired
	private IFeignSys feignSys;

	@Autowired
	private SendSmsLogDao dao;
	@Autowired
	private PlatformDao platformDao;
	@Autowired
	private UserDao userDao;
	@Autowired
	private RedisTemplate<String, String> redisTemplate;

	public Result<Page<SendSmsLogPageRESQ>> listForPage(SendSmsLogPageREQ req) {
		SendSmsLogExample example = new SendSmsLogExample();
		Criteria c = example.createCriteria();
		if (StringUtils.hasText(req.getMobile())) {
			c.andMobileEqualTo(req.getMobile());
		}
		if (StringUtils.hasText(req.getBeginGmtCreate())) {
			c.andGmtCreateGreaterThanOrEqualTo(DateUtil.parseDate(req.getBeginGmtCreate(), "yyyy-MM-dd"));
		}
		if (StringUtils.hasText(req.getEndGmtCreate())) {
			c.andGmtCreateLessThanOrEqualTo(DateUtil.addDate(DateUtil.parseDate(req.getEndGmtCreate(), "yyyy-MM-dd"), 1));
		}
		example.setOrderByClause(" id desc ");
		Page<SendSmsLog> page = dao.listForPage(req.getPageCurrent(), req.getPageSize(), example);
		return Result.success(PageUtil.transform(page, SendSmsLogPageRESQ.class));
	}

	public Result<Integer> send(SendSmsLogSendREQ req) {
		if (StringUtils.isEmpty(req.getMobile())) {
			return Result.error("?????????????????????");
		}
		// ??????????????????
		if (!Pattern.compile(REGEX_MOBILE).matcher(req.getMobile()).matches()) {
			return Result.error("???????????????????????????");
		}
		User user = userDao.getByMobile(req.getMobile());
		if (ObjectUtil.isNull(user)) {
			return Result.error("???????????????");
		}
		Platform platform = platformDao.getByClientId(user.getUserSource());
		if (!StatusIdEnum.YES.getCode().equals(platform.getStatusId())) {
			return Result.error("??????????????????");
		}

		if (redisTemplate.hasKey(platform.getClientId() + req.getMobile())) {
			return Result.error("????????????????????????????????????????????????5?????????");
		}
		SysVO sys = feignSys.getSys();
		if (ObjectUtil.isNull(sys)) {
			return Result.error("????????????????????????");
		}
		if (StringUtils.isEmpty(sys.getAliyunAccessKeyId()) || StringUtils.isEmpty(sys.getAliyunAccessKeySecret())) {
			return Result.error("aliyunAccessKeyId???aliyunAccessKeySecret?????????");
		}
		if (StringUtils.isEmpty(sys.getSmsCode()) || StringUtils.isEmpty(sys.getSignName())) {
			return Result.error("smsCode???signName?????????");
		}
		// ??????????????????
		SendSmsLog sendSmsLog = new SendSmsLog();
		sendSmsLog.setMobile(req.getMobile());
		sendSmsLog.setTemplate(sys.getSmsCode());
		// ?????????????????????
		sendSmsLog.setSmsCode(RandomUtil.randomNumbers(6));
		try {
			// ???????????????
			boolean result = AliyunUtil.sendMsg(req.getMobile(), sendSmsLog.getSmsCode(), BeanUtil.copyProperties(sys, Aliyun.class));
			if (result) {
				// ???????????????????????????????????????5????????????
				redisTemplate.opsForValue().set(platform.getClientId() + req.getMobile(), sendSmsLog.getSmsCode(), 5, TimeUnit.MINUTES);
				sendSmsLog.setIsSuccess(IsSuccessEnum.SUCCESS.getCode());
				int results = dao.save(sendSmsLog);
				if (results > 0) {
					return Result.success(results);
				}
				return Result.error(ResultEnum.USER_SEND_FAIL);
			}
			// ????????????
			sendSmsLog.setIsSuccess(IsSuccessEnum.FAIL.getCode());
			dao.save(sendSmsLog);
			return Result.error(ResultEnum.USER_SEND_FAIL);
		} catch (ClientException e) {
			sendSmsLog.setIsSuccess(IsSuccessEnum.FAIL.getCode());
			dao.save(sendSmsLog);
			logger.error("?????????????????????={}", e);
			return Result.error(ResultEnum.USER_SEND_FAIL);
		}
	}

}
