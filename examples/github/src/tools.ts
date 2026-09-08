import { wrap } from './utils/wrap';
import { searchRepositories, getRepository } from './github/repos';
import { listIssues, getIssue, createIssue, updateIssue, commentIssue, listIssueComments } from './github/issues';
import {
    listPullRequests,
    createPullRequest,
    getPullRequest,
    mergePullRequest,
    updatePullRequest,
    listPullRequestFiles,
    getPullRequestDiff,
    listPullRequestCommits,
    listPullRequestReviews,
    listReviewComments,
    createReview,
    replyReviewComment,
    requestReviewers
} from './github/pulls';
import { getFileContent, createOrUpdateFile, deleteFile } from './github/contents';
import { createBranch } from './github/branches';
import { patchFileInRepo } from './github/patch';
import {
    listWorkflows,
    listWorkflowRuns,
    getWorkflowRun,
    triggerWorkflow,
    getWorkflowJobs,
    rerunWorkflowRun,
    cancelWorkflowRun,
    listCheckRuns
} from './github/actions';
import { listBranches, listCommits, getCommit, compareRefs, getCompareDiff } from './github/git';
import { searchCode, searchIssues } from './github/search';
import { getAuthenticatedUser, getRateLimit } from './github/users';
import { forkRepository, syncFork } from './github/forks';
import { applyLocalReplace, applyLocalDelete, overwriteLocalFile } from './local/fileApply';
import { terminalExec } from './local/terminal';

export const toolImpl = {
    search_repositories: (p: any) => wrap(searchRepositories, p, '搜索仓库成功', '搜索仓库失败'),
    get_repository: (p: any) => wrap(getRepository, p, '获取仓库信息成功', '获取仓库信息失败'),

    list_issues: (p: any) => wrap(listIssues, p, '获取 Issues 成功', '获取 Issues 失败'),
    get_issue: (p: any) => wrap(getIssue, p, '获取 Issue 成功', '获取 Issue 失败'),
    create_issue: (p: any) => wrap(createIssue, p, '创建 Issue 成功', '创建 Issue 失败'),
    update_issue: (p: any) => wrap(updateIssue, p, '更新 Issue 成功', '更新 Issue 失败'),
    comment_issue: (p: any) => wrap(commentIssue, p, '发表评论成功', '发表评论失败'),
    list_issue_comments: (p: any) => wrap(listIssueComments, p, '获取 Issue 评论成功', '获取 Issue 评论失败'),

    list_pull_requests: (p: any) => wrap(listPullRequests, p, '获取 PR 列表成功', '获取 PR 列表失败'),
    create_pull_request: (p: any) => wrap(createPullRequest, p, '创建 PR 成功', '创建 PR 失败'),
    get_pull_request: (p: any) => wrap(getPullRequest, p, '获取 PR 成功', '获取 PR 失败'),
    update_pull_request: (p: any) => wrap(updatePullRequest, p, '更新 PR 成功', '更新 PR 失败'),
    merge_pull_request: (p: any) => wrap(mergePullRequest, p, '合并 PR 成功', '合并 PR 失败'),
    list_pull_request_files: (p: any) => wrap(listPullRequestFiles, p, '获取 PR 文件成功', '获取 PR 文件失败'),
    get_pull_request_diff: (p: any) => wrap(getPullRequestDiff, p, '获取 PR diff 成功', '获取 PR diff 失败'),
    list_pull_request_commits: (p: any) => wrap(listPullRequestCommits, p, '获取 PR 提交成功', '获取 PR 提交失败'),
    list_pull_request_reviews: (p: any) => wrap(listPullRequestReviews, p, '获取 PR review 成功', '获取 PR review 失败'),
    list_review_comments: (p: any) => wrap(listReviewComments, p, '获取行内评论成功', '获取行内评论失败'),
    create_review: (p: any) => wrap(createReview, p, '提交 review 成功', '提交 review 失败'),
    reply_review_comment: (p: any) => wrap(replyReviewComment, p, '回复行内评论成功', '回复行内评论失败'),
    request_reviewers: (p: any) => wrap(requestReviewers, p, '请求 reviewer 成功', '请求 reviewer 失败'),

    get_file_content: (p: any) => wrap(getFileContent, p, '读取文件成功', '读取文件失败'),
    create_or_update_file: (p: any) => wrap(createOrUpdateFile, p, '提交文件成功', '提交文件失败'),
    delete_file: (p: any) => wrap(deleteFile, p, '删除文件成功', '删除文件失败'),
    create_branch: (p: any) => wrap(createBranch, p, '创建分支成功', '创建分支失败'),
    patch_file_in_repo: (p: any) => wrap(patchFileInRepo, p, '仓库文件差异更新成功', '仓库文件差异更新失败'),

    list_workflows: (p: any) => wrap(listWorkflows, p, '获取 workflow 成功', '获取 workflow 失败'),
    list_workflow_runs: (p: any) => wrap(listWorkflowRuns, p, '获取 workflow run 成功', '获取 workflow run 失败'),
    get_workflow_run: (p: any) => wrap(getWorkflowRun, p, '获取 workflow run 详情成功', '获取 workflow run 详情失败'),
    trigger_workflow: (p: any) => wrap(triggerWorkflow, p, '触发 workflow 成功', '触发 workflow 失败'),
    get_workflow_jobs: (p: any) => wrap(getWorkflowJobs, p, '获取 workflow jobs 成功', '获取 workflow jobs 失败'),
    rerun_workflow_run: (p: any) => wrap(rerunWorkflowRun, p, '重跑 workflow 成功', '重跑 workflow 失败'),
    cancel_workflow_run: (p: any) => wrap(cancelWorkflowRun, p, '取消 workflow 成功', '取消 workflow 失败'),
    list_check_runs: (p: any) => wrap(listCheckRuns, p, '获取 check runs 成功', '获取 check runs 失败'),

    list_branches: (p: any) => wrap(listBranches, p, '获取分支成功', '获取分支失败'),
    list_commits: (p: any) => wrap(listCommits, p, '获取提交成功', '获取提交失败'),
    get_commit: (p: any) => wrap(getCommit, p, '获取提交详情成功', '获取提交详情失败'),
    compare_refs: (p: any) => wrap(compareRefs, p, '比较引用成功', '比较引用失败'),
    get_compare_diff: (p: any) => wrap(getCompareDiff, p, '获取 compare diff 成功', '获取 compare diff 失败'),

    search_code: (p: any) => wrap(searchCode, p, '搜索代码成功', '搜索代码失败'),
    search_issues: (p: any) => wrap(searchIssues, p, '搜索 Issue/PR 成功', '搜索 Issue/PR 失败'),
    get_authenticated_user: (p: any) => wrap(getAuthenticatedUser, p, '获取当前用户成功', '获取当前用户失败'),
    get_rate_limit: (p: any) => wrap(getRateLimit, p, '获取速率限制成功', '获取速率限制失败'),
    fork_repository: (p: any) => wrap(forkRepository, p, 'Fork 仓库成功', 'Fork 仓库失败'),
    sync_fork: (p: any) => wrap(syncFork, p, '同步 fork 成功', '同步 fork 失败'),

    apply_local_replace: (p: any) => wrap(applyLocalReplace as any, p, '本地差异更新成功', '本地差异更新失败'),
    apply_local_delete: (p: any) => wrap(applyLocalDelete as any, p, '本地删除片段成功', '本地删除片段失败'),
    overwrite_local_file: (p: any) => wrap(overwriteLocalFile as any, p, '本地覆盖写入成功', '本地覆盖写入失败'),
    terminal_exec: (p: any) => wrap(terminalExec as any, p, '终端执行成功', '终端执行失败')
};