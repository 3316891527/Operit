import { buildUrl, compactCommit, compactFileChange, parseJsonParam, repoPath, requestJson, requestText, requireToken, splitCsv, truncateText } from './api';

export type RepoIdParams = {
    owner: string;
    repo: string;
};

export type ListPullRequestsParams = RepoIdParams & {
    state?: 'open' | 'closed' | 'all' | string;
    head?: string;
    base?: string;
    page?: number;
    per_page?: number;
};

export type CreatePullRequestParams = RepoIdParams & {
    title: string;
    head: string;
    base: string;
    body?: string;
    draft?: boolean;
};

export type GetPullRequestParams = RepoIdParams & {
    pull_number: number;
};

export type MergePullRequestParams = RepoIdParams & {
    pull_number: number;
    commit_title?: string;
    commit_message?: string;
    merge_method?: 'merge' | 'squash' | 'rebase' | string;
};

function compactPull(pr: any) {
    if (!pr) return pr;
    return {
        number: pr.number,
        title: pr.title,
        state: pr.state,
        draft: pr.draft,
        merged: pr.merged,
        mergeable: pr.mergeable,
        html_url: pr.html_url,
        user: pr.user?.login,
        head: pr.head?.label || pr.head?.ref,
        head_sha: pr.head?.sha,
        base: pr.base?.label || pr.base?.ref,
        body: pr.body,
        created_at: pr.created_at,
        updated_at: pr.updated_at
    };
}

export async function listPullRequests(params: ListPullRequestsParams): Promise<any[]> {
    const url = buildUrl(repoPath(params.owner, params.repo, '/pulls'), {
        state: params.state ?? 'open',
        head: params.head,
        base: params.base,
        page: params.page ?? 1,
        per_page: params.per_page ?? 30
    });
    const items = await requestJson<any[]>({ method: 'GET', url });
    return Array.isArray(items) ? items.map(compactPull) : [];
}

export async function createPullRequest(params: CreatePullRequestParams): Promise<any> {
    requireToken('create_pull_request');
    const url = buildUrl(repoPath(params.owner, params.repo, '/pulls'));
    return compactPull(
        await requestJson<any>({
            method: 'POST',
            url,
            body: {
                title: params.title,
                head: params.head,
                base: params.base,
                body: params.body,
                draft: params.draft
            }
        })
    );
}

export async function getPullRequest(params: GetPullRequestParams): Promise<any> {
    const url = buildUrl(repoPath(params.owner, params.repo, `/pulls/${params.pull_number}`));
    return compactPull(await requestJson<any>({ method: 'GET', url }));
}

export async function mergePullRequest(params: MergePullRequestParams): Promise<any> {
    requireToken('merge_pull_request');
    const url = buildUrl(repoPath(params.owner, params.repo, `/pulls/${params.pull_number}/merge`));
    return requestJson<any>({
        method: 'PUT',
        url,
        body: {
            commit_title: params.commit_title,
            commit_message: params.commit_message,
            merge_method: params.merge_method
        }
    });
}

export async function updatePullRequest(
    params: RepoIdParams & {
        pull_number: number;
        title?: string;
        body?: string;
        state?: string;
        base?: string;
        draft?: boolean;
    }
): Promise<any> {
    requireToken('update_pull_request');
    const url = buildUrl(repoPath(params.owner, params.repo, `/pulls/${params.pull_number}`));
    return compactPull(
        await requestJson<any>({
            method: 'PATCH',
            url,
            body: {
                title: params.title,
                body: params.body,
                state: params.state,
                base: params.base,
                draft: params.draft
            }
        })
    );
}

export async function listPullRequestFiles(
    params: RepoIdParams & { pull_number: number; include_patch?: boolean; page?: number; per_page?: number }
): Promise<any> {
    const url = buildUrl(repoPath(params.owner, params.repo, `/pulls/${params.pull_number}/files`), {
        page: params.page ?? 1,
        per_page: params.per_page ?? 100
    });
    const items = await requestJson<any[]>({ method: 'GET', url });
    return Array.isArray(items) ? items.map((file) => compactFileChange(file, params.include_patch === true)) : [];
}

export async function getPullRequestDiff(
    params: RepoIdParams & { pull_number: number; max_chars?: number }
): Promise<any> {
    const url = buildUrl(repoPath(params.owner, params.repo, `/pulls/${params.pull_number}`));
    const resp = await requestText({
        method: 'GET',
        url,
        headers: { Accept: 'application/vnd.github.diff' },
        timeoutMs: 60000
    });
    const truncated = truncateText(resp.text, params.max_chars ?? 20000);
    return {
        pull_number: params.pull_number,
        diff: truncated.text,
        truncated: truncated.truncated,
        original_length: truncated.original_length
    };
}

export async function listPullRequestCommits(
    params: RepoIdParams & { pull_number: number; page?: number; per_page?: number }
): Promise<any> {
    const url = buildUrl(repoPath(params.owner, params.repo, `/pulls/${params.pull_number}/commits`), {
        page: params.page ?? 1,
        per_page: params.per_page ?? 30
    });
    const items = await requestJson<any[]>({ method: 'GET', url });
    return Array.isArray(items) ? items.map(compactCommit) : [];
}

export async function listPullRequestReviews(
    params: RepoIdParams & { pull_number: number; page?: number; per_page?: number }
): Promise<any> {
    const url = buildUrl(repoPath(params.owner, params.repo, `/pulls/${params.pull_number}/reviews`), {
        page: params.page ?? 1,
        per_page: params.per_page ?? 30
    });
    const items = await requestJson<any[]>({ method: 'GET', url });
    return Array.isArray(items)
        ? items.map((item) => ({
              id: item.id,
              user: item.user?.login,
              state: item.state,
              body: item.body,
              submitted_at: item.submitted_at,
              html_url: item.html_url,
              commit_id: item.commit_id
          }))
        : [];
}

export async function listReviewComments(
    params: RepoIdParams & { pull_number: number; page?: number; per_page?: number }
): Promise<any> {
    const url = buildUrl(repoPath(params.owner, params.repo, `/pulls/${params.pull_number}/comments`), {
        page: params.page ?? 1,
        per_page: params.per_page ?? 30
    });
    const items = await requestJson<any[]>({ method: 'GET', url });
    return Array.isArray(items)
        ? items.map((item) => ({
              id: item.id,
              user: item.user?.login,
              path: item.path,
              line: item.line,
              original_line: item.original_line,
              side: item.side,
              body: item.body,
              html_url: item.html_url,
              in_reply_to_id: item.in_reply_to_id,
              created_at: item.created_at,
              updated_at: item.updated_at
          }))
        : [];
}

export async function createReview(
    params: RepoIdParams & {
        pull_number: number;
        body?: string;
        event?: string;
        commit_id?: string;
        comments?: any;
    }
): Promise<any> {
    requireToken('create_review');
    const comments = parseJsonParam<any[]>(params.comments, 'comments');
    const url = buildUrl(repoPath(params.owner, params.repo, `/pulls/${params.pull_number}/reviews`));
    const data = await requestJson<any>({
        method: 'POST',
        url,
        body: {
            body: params.body,
            event: params.event,
            commit_id: params.commit_id,
            comments
        }
    });
    return {
        id: data.id,
        state: data.state,
        body: data.body,
        html_url: data.html_url,
        user: data.user?.login
    };
}

export async function replyReviewComment(
    params: RepoIdParams & { pull_number: number; comment_id: number; body: string }
): Promise<any> {
    requireToken('reply_review_comment');
    const url = buildUrl(
        repoPath(params.owner, params.repo, `/pulls/${params.pull_number}/comments/${params.comment_id}/replies`)
    );
    const data = await requestJson<any>({
        method: 'POST',
        url,
        body: { body: params.body }
    });
    return {
        id: data.id,
        body: data.body,
        html_url: data.html_url,
        in_reply_to_id: data.in_reply_to_id,
        user: data.user?.login
    };
}

export async function requestReviewers(
    params: RepoIdParams & { pull_number: number; reviewers?: string | string[]; team_reviewers?: string | string[] }
): Promise<any> {
    requireToken('request_reviewers');
    const url = buildUrl(repoPath(params.owner, params.repo, `/pulls/${params.pull_number}/requested_reviewers`));
    const data = await requestJson<any>({
        method: 'POST',
        url,
        body: {
            reviewers: splitCsv(params.reviewers),
            team_reviewers: splitCsv(params.team_reviewers)
        }
    });
    return {
        number: data.number,
        html_url: data.html_url,
        requested_reviewers: Array.isArray(data.requested_reviewers) ? data.requested_reviewers.map((it: any) => it.login) : [],
        requested_teams: Array.isArray(data.requested_teams) ? data.requested_teams.map((it: any) => it.slug || it.name) : []
    };
}