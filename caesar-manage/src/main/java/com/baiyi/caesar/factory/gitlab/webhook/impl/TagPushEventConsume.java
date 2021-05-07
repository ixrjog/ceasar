package com.baiyi.caesar.factory.gitlab.webhook.impl;

import com.baiyi.caesar.common.base.GitlabEventType;
import com.baiyi.caesar.domain.generator.caesar.*;
import com.baiyi.caesar.factory.gitlab.webhook.IWebhookEventConsume;
import com.baiyi.caesar.factory.jenkins.BuildJobHandlerFactory;
import com.baiyi.caesar.factory.jenkins.IBuildJobHandler;
import com.baiyi.caesar.gitlab.handler.GitlabBranchHandler;
import com.baiyi.caesar.service.application.CsApplicationScmMemberService;
import com.baiyi.caesar.service.gitlab.CsGitlabProjectService;
import lombok.extern.slf4j.Slf4j;
import org.gitlab.api.models.GitlabTag;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.List;

/**
 * @Author baiyi
 * @Date 2021/5/6 4:31 下午
 * @Version 1.0
 */
@Slf4j
@Component
public class TagPushEventConsume extends BaseWebhookEventConsume implements IWebhookEventConsume {

    public static final String TAG_REF_PREFIX = "refs/tags/";

    @Resource
    private GitlabBranchHandler gitlabBranchHandler;

    @Resource
    private CsApplicationScmMemberService csApplicationScmMemberService;

    @Resource
    private CsGitlabProjectService csGitlabProjectService;

    @Override
    public String getEventKey() {
        return GitlabEventType.TAG_PUSH.getDesc();
    }

    @Override
    public void consume(CsGitlabWebhook csGitlabWebhook) {
        // 判断tag是否存在
        String tag = csGitlabWebhook.getRef().replace(TAG_REF_PREFIX, "");
        CsGitlabInstance gitlabInstance = getGitlabInstanceById(csGitlabWebhook.getInstanceId());
        List<GitlabTag> tags = gitlabBranchHandler.getTags(gitlabInstance.getName(), csGitlabWebhook.getProjectId());
        if (tags.stream().noneMatch(e -> e.getName().equals(tag))) return; // tag不存在

        CsGitlabProject csGitlabProject = csGitlabProjectService.queryCsGitlabProjectByUniqueKey(gitlabInstance.getId(), csGitlabWebhook.getProjectId());
        if (csGitlabProject == null) return;
        List<CsApplicationScmMember> scmMembers = csApplicationScmMemberService.queryCsApplicationScmMemberByScmId(csGitlabProject.getId());
        if (CollectionUtils.isEmpty(scmMembers)) return;
        scmMembers.forEach(e -> consumer(e, csGitlabWebhook, tag));
    }

    private void consumer(CsApplicationScmMember scmMember, CsGitlabWebhook csGitlabWebhook, String tag) {

        // 查询对应的job
        List<CsCiJob> ciJobs = csCiJobService.queryCsCiJobByScmMemberId(scmMember.getId());
        if (!CollectionUtils.isEmpty(ciJobs)) {
            ciJobs.forEach(job -> {
                IBuildJobHandler buildJobHandler = BuildJobHandlerFactory.getBuildJobByKey(job.getJobType());
                buildJobHandler.build(job, csGitlabWebhook.getUsername());
                csGitlabWebhook.setIsConsumed(true);
                csGitlabWebhook.setIsTrigger(true);
                csGitlabWebhook.setJobKey(job.getJobKey());
                updateEvent(csGitlabWebhook);
            });
        }

    }
}
