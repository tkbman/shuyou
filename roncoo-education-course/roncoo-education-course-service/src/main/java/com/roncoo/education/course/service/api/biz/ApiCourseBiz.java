package com.roncoo.education.course.service.api.biz;

import java.util.List;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder.Field;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.roncoo.education.course.common.bo.CourseInfoPageBO;
import com.roncoo.education.course.common.bo.CourseInfoSearchBO;
import com.roncoo.education.course.common.bo.CourseVideoBO;
import com.roncoo.education.course.common.dto.CourseChapterDTO;
import com.roncoo.education.course.common.dto.CourseChapterPeriodDTO;
import com.roncoo.education.course.common.dto.CourseInfoPageDTO;
import com.roncoo.education.course.common.dto.CourseInfoSearchPageDTO;
import com.roncoo.education.course.common.dto.CourseIntroduceDTO;
import com.roncoo.education.course.common.dto.CourseViewDTO;
import com.roncoo.education.course.common.dto.LecturerDTO;
import com.roncoo.education.course.common.es.EsCourse;
import com.roncoo.education.course.common.es.EsPageUtil;
import com.roncoo.education.course.common.es.ResultMapperExt;
import com.roncoo.education.course.service.dao.CourseChapterDao;
import com.roncoo.education.course.service.dao.CourseChapterPeriodDao;
import com.roncoo.education.course.service.dao.CourseDao;
import com.roncoo.education.course.service.dao.CourseIntroduceDao;
import com.roncoo.education.course.service.dao.impl.mapper.entity.Course;
import com.roncoo.education.course.service.dao.impl.mapper.entity.CourseChapter;
import com.roncoo.education.course.service.dao.impl.mapper.entity.CourseChapterPeriod;
import com.roncoo.education.course.service.dao.impl.mapper.entity.CourseExample;
import com.roncoo.education.course.service.dao.impl.mapper.entity.CourseExample.Criteria;
import com.roncoo.education.course.service.dao.impl.mapper.entity.CourseIntroduce;
import com.roncoo.education.user.feign.vo.LecturerVO;
import com.roncoo.education.user.feign.interfaces.IFeignLecturer;
import com.roncoo.education.util.base.Page;
import com.roncoo.education.util.base.PageUtil;
import com.roncoo.education.util.base.Result;
import com.roncoo.education.util.enums.IsHfield;
import com.roncoo.education.util.enums.IsPutawayEnum;
import com.roncoo.education.util.enums.StatusIdEnum;
import com.roncoo.education.util.tools.BeanUtil;
import com.roncoo.education.util.tools.SqlUtil;
import com.xiaoleilu.hutool.util.CollectionUtil;

/**
 * ????????????
 *
 * @author wujing
 */
@Component
public class ApiCourseBiz {

	@Autowired
	private CourseDao courseDao;
	@Autowired
	private CourseIntroduceDao courseIntroduceDao;
	@Autowired
	private CourseChapterDao courseChapterDao;
	@Autowired
	private CourseChapterPeriodDao courseChapterPeriodDao;
	@Autowired
	private IFeignLecturer bossLecturer;

	@Autowired(required = false)
	private ElasticsearchTemplate elasticsearchTemplate;

	@Autowired
	private ResultMapperExt resultMapperExt;

	/**
	 * ??????????????????
	 *
	 * @param courseView
	 * @return
	 */
	public Result<CourseViewDTO> view(CourseVideoBO courseVideoBO) {
		if (courseVideoBO.getCourseId() == null) {
			return Result.error("??????ID????????????");
		}
		// ????????????
		Course course = courseDao.getById(courseVideoBO.getCourseId());
		if (course == null) {
			return Result.error("????????????????????????");
		}
		CourseViewDTO data = BeanUtil.copyProperties(course, CourseViewDTO.class);

		// ????????????
		CourseIntroduce courseIntroduce = courseIntroduceDao.getById(data.getIntroduceId());
		if (!StringUtils.isEmpty(courseIntroduce)) {
			data.setIntroduce(BeanUtil.copyProperties(courseIntroduce, CourseIntroduceDTO.class).getIntroduce());
		}

		// ????????????
		LecturerVO lecturerVO = bossLecturer.getByLecturerUserNo(data.getLecturerUserNo());
		if (StringUtils.isEmpty(lecturerVO)) {
			return Result.error("??????????????????????????????????????????????????????!");
		}
		data.setLecturer(BeanUtil.copyProperties(lecturerVO, LecturerDTO.class));

		// ????????????
		List<CourseChapter> courseChapterList = courseChapterDao.listByCourseIdAndStatusId(courseVideoBO.getCourseId(), StatusIdEnum.YES.getCode());
		if (CollectionUtil.isNotEmpty(courseChapterList)) {
			data.setChapterList(PageUtil.copyList(courseChapterList, CourseChapterDTO.class));
		}

		// ????????????
		if (CollectionUtil.isNotEmpty(data.getChapterList())) {
			for (CourseChapterDTO courseChapterDTO : data.getChapterList()) {
				List<CourseChapterPeriod> courseChapterPeriodList = courseChapterPeriodDao.listByChapterIdAndStatusId(courseChapterDTO.getId(), StatusIdEnum.YES.getCode());
				courseChapterDTO.setPeriodList(PageUtil.copyList(courseChapterPeriodList, CourseChapterPeriodDTO.class));
			}
		}
		return Result.success(data);
	}

	/**
	 * ????????????????????????
	 *
	 * @param courseInfoPageBO
	 * @return
	 * @author wuyun
	 */
	public Result<Page<CourseInfoPageDTO>> list(CourseInfoPageBO courseInfoPageBO) {
		CourseExample example = new CourseExample();
		Criteria c = example.createCriteria();
		c.andStatusIdEqualTo(StatusIdEnum.YES.getCode());
		c.andIsPutawayEqualTo(IsPutawayEnum.YES.getCode());
		if (!StringUtils.isEmpty(courseInfoPageBO.getCategoryId1())) {
			c.andCategoryId1EqualTo(courseInfoPageBO.getCategoryId1());
		}
		if (!StringUtils.isEmpty(courseInfoPageBO.getCategoryId2())) {
			c.andCategoryId2EqualTo(courseInfoPageBO.getCategoryId2());
		}
		if (!StringUtils.isEmpty(courseInfoPageBO.getCategoryId3())) {
			c.andCategoryId3EqualTo(courseInfoPageBO.getCategoryId3());
		}
		if (!StringUtils.isEmpty(courseInfoPageBO.getIsFree())) {
			c.andIsFreeEqualTo(courseInfoPageBO.getIsFree());
		}
		if (!StringUtils.isEmpty(courseInfoPageBO.getCourseName())) {
			c.andCourseNameLike(PageUtil.rightLike(SqlUtil.checkSql(courseInfoPageBO.getCourseName())));
		}
		example.setOrderByClause(" sort desc, id desc ");
		Page<Course> page = courseDao.listForPage(courseInfoPageBO.getPageCurrent(), courseInfoPageBO.getPageSize(), example);
		return Result.success(PageUtil.transform(page, CourseInfoPageDTO.class));
	}

	/**
	 * ????????????????????????
	 *
	 * @param courseInfoSearchBO
	 * @author wuyun
	 */
	public Result<Page<CourseInfoSearchPageDTO>> searchList(CourseInfoSearchBO bo) {
		if (StringUtils.isEmpty(bo.getOrgNo())) {
			return Result.error("orgNo????????????");
		}
		if (bo.getPageCurrent() <= 0) {
			bo.setPageCurrent(1);
		}
		if (bo.getPageSize() <= 0) {
			bo.setPageSize(20);
		}

		if (StringUtils.isEmpty(bo.getCourseName())) {
			return Result.success(new Page<CourseInfoSearchPageDTO>());
		}

		String heightField = "courseName";

		Field hfield = null;
		if (bo.getIsHfield() != null && bo.getIsHfield().equals(IsHfield.YES.getCode())) {
			hfield = new HighlightBuilder.Field(heightField).preTags("<mark>").postTags("</mark>");
		}

		NativeSearchQueryBuilder nsb = new NativeSearchQueryBuilder();
		if (bo.getIsHfield() != null && bo.getIsHfield().equals(IsHfield.YES.getCode())) {
			nsb.withHighlightFields(hfield);// ????????????
		}
		nsb.withSort(SortBuilders.scoreSort().order(SortOrder.DESC));// ???????????????_source???
		nsb.withSort(new FieldSortBuilder("courseSort").order(SortOrder.DESC));// ???????????????courseSort???
		nsb.withPageable(PageRequest.of(bo.getPageCurrent() - 1, bo.getPageSize()));
		// ?????????????????????boolQuery
		BoolQueryBuilder qb = QueryBuilders.boolQuery();
		// ????????????termQuery????????????must???????????????AND
		qb.must(QueryBuilders.termQuery("orgNo", bo.getOrgNo()));
		// ????????????multiMatchQuery???????????????best_fields
		qb.must(QueryBuilders.multiMatchQuery(bo.getCourseName(), "courseName", "lecturerName").type(MultiMatchQueryBuilder.Type.BEST_FIELDS));

		nsb.withQuery(qb);

		org.springframework.data.domain.Page<EsCourse> page = elasticsearchTemplate.queryForPage(nsb.build(), EsCourse.class, resultMapperExt);
		return Result.success(EsPageUtil.transform(page, CourseInfoSearchPageDTO.class));
	}
}
