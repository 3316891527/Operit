import { buildUrl, repoPath, requestJson, requireToken } from './api';

export type RepoIdParams = {
    owner: string;
    repo: string;
};

function compactMilestone(item: any) {
    if (!item) return item;
    return {
        id: item.id,
        number: item.number,
        title: item.title,
        description: item.description,
        state: item.state,
        html_url: item.html_url,
        open_issues: item.open_issues,
        closed_issues: item.closed_issues,
        due_on: item.due_on,
        created_at: item.created_at,
        updated_at: item.updated_at,
        closed_at: item.closed_at,
        creator: item.creator?.login
    };
}

export async function listMilestones(
    params: RepoIdParams & { state?: string; sort?: string; direction?: string; page?: number; per_page?: number }
): Promise<any> {
    const url = buildUrl(repoPath(params.owner, params.repo, '/milestones'), {
        state: params.state ?? 'open',
        sort: params.sort,
        direction: params.direction,
        page: params.page ?? 1,
        per_page: params.per_page ?? 30
    });
    const items = await requestJson<any[]>({ method: 'GET', url });
    return Array.isArray(items) ? items.map(compactMilestone) : [];
}

export async function getMilestone(params: RepoIdParams & { milestone_number: number | string }): Promise<any> {
    const url = buildUrl(
        repoPath(params.owner, params.repo, `/milestones/${encodeURIComponent(String(params.milestone_number))}`)
    );
    return compactMilestone(await requestJson<any>({ method: 'GET', url }));
}

export async function createMilestone(
    params: RepoIdParams & { title: string; state?: string; description?: string; due_on?: string }
): Promise<any> {
    requireToken('create_milestone');
    const url = buildUrl(repoPath(params.owner, params.repo, '/milestones'));
    return compactMilestone(
        await requestJson<any>({
            method: 'POST',
            url,
            body: {
                title: params.title,
                state: params.state,
                description: params.description,
                due_on: params.due_on
            }
        })
    );
}

export async function updateMilestone(
    params: RepoIdParams & {
        milestone_number: number | string;
        title?: string;
        state?: string;
        description?: string;
        due_on?: string;
    }
): Promise<any> {
    requireToken('update_milestone');
    const url = buildUrl(
        repoPath(params.owner, params.repo, `/milestones/${encodeURIComponent(String(params.milestone_number))}`)
    );
    return compactMilestone(
        await requestJson<any>({
            method: 'PATCH',
            url,
            body: {
                title: params.title,
                state: params.state,
                description: params.description,
                due_on: params.due_on
            }
        })
    );
}

export async function deleteMilestone(params: RepoIdParams & { milestone_number: number | string }): Promise<any> {
    requireToken('delete_milestone');
    const url = buildUrl(
        repoPath(params.owner, params.repo, `/milestones/${encodeURIComponent(String(params.milestone_number))}`)
    );
    const result = await requestJson<any>({ method: 'DELETE', url });
    return { ok: true, milestone_number: params.milestone_number, result };
}
