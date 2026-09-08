import { buildUrl, repoPath, requestJson, requireToken, splitCsv } from './api';

export type RepoIdParams = {
    owner: string;
    repo: string;
};

export type ListIssuesParams = RepoIdParams & {
    state?: 'open' | 'closed' | 'all' | string;
    labels?: string;
    creator?: string;
    page?: number;
    per_page?: number;
    include_pull_requests?: boolean;
};

export type CreateIssueParams = RepoIdParams & {
    title: string;
    body?: string;
    labels?: string[] | string;
    assignees?: string[] | string;
};

export type CommentIssueParams = RepoIdParams & {
    issue_number: number;
    body: string;
};

export type ListIssueCommentsParams = RepoIdParams & {
    issue_number: number;
    page?: number;
    per_page?: number;
};

function compactIssue(item: any) {
    if (!item) return item;
    return {
        number: item.number,
        title: item.title,
        state: item.state,
        html_url: item.html_url,
        user: item.user?.login,
        labels: Array.isArray(item.labels) ? item.labels.map((it: any) => (typeof it === 'string' ? it : it.name)) : [],
        assignees: Array.isArray(item.assignees) ? item.assignees.map((it: any) => it.login) : [],
        comments: item.comments,
        pull_request: Boolean(item.pull_request),
        created_at: item.created_at,
        updated_at: item.updated_at,
        body: item.body
    };
}

export async function listIssues(params: ListIssuesParams): Promise<any[]> {
    const url = buildUrl(repoPath(params.owner, params.repo, '/issues'), {
        state: params.state ?? 'open',
        labels: params.labels,
        creator: params.creator,
        page: params.page ?? 1,
        per_page: params.per_page ?? 30
    });
    const items = await requestJson<any[]>({ method: 'GET', url });
    const includePRs = params.include_pull_requests === true;
    const filtered = includePRs ? items : items.filter((it) => !it || !it.pull_request);
    return Array.isArray(filtered) ? filtered.map(compactIssue) : [];
}

export async function getIssue(params: RepoIdParams & { issue_number: number }): Promise<any> {
    const url = buildUrl(repoPath(params.owner, params.repo, `/issues/${params.issue_number}`));
    return compactIssue(await requestJson<any>({ method: 'GET', url }));
}

export async function createIssue(params: CreateIssueParams): Promise<any> {
    requireToken('create_issue');
    const url = buildUrl(repoPath(params.owner, params.repo, '/issues'));
    return compactIssue(
        await requestJson<any>({
            method: 'POST',
            url,
            body: {
                title: params.title,
                body: params.body,
                labels: splitCsv(params.labels),
                assignees: splitCsv(params.assignees)
            }
        })
    );
}

export async function updateIssue(
    params: RepoIdParams & {
        issue_number: number;
        title?: string;
        body?: string;
        state?: string;
        labels?: string[] | string;
        assignees?: string[] | string;
    }
): Promise<any> {
    requireToken('update_issue');
    const url = buildUrl(repoPath(params.owner, params.repo, `/issues/${params.issue_number}`));
    return compactIssue(
        await requestJson<any>({
            method: 'PATCH',
            url,
            body: {
                title: params.title,
                body: params.body,
                state: params.state,
                labels: splitCsv(params.labels),
                assignees: splitCsv(params.assignees)
            }
        })
    );
}

export async function commentIssue(params: CommentIssueParams): Promise<any> {
    requireToken('comment_issue');
    const url = buildUrl(repoPath(params.owner, params.repo, `/issues/${params.issue_number}/comments`));
    const data = await requestJson<any>({ method: 'POST', url, body: { body: params.body } });
    return {
        id: data.id,
        html_url: data.html_url,
        user: data.user?.login,
        body: data.body,
        created_at: data.created_at
    };
}

export async function listIssueComments(params: ListIssueCommentsParams): Promise<any[]> {
    const url = buildUrl(repoPath(params.owner, params.repo, `/issues/${params.issue_number}/comments`), {
        page: params.page ?? 1,
        per_page: params.per_page ?? 30
    });
    const items = await requestJson<any[]>({ method: 'GET', url });
    return Array.isArray(items)
        ? items.map((item) => ({
              id: item.id,
              html_url: item.html_url,
              user: item.user?.login,
              body: item.body,
              created_at: item.created_at,
              updated_at: item.updated_at
          }))
        : [];
}