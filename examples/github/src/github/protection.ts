import { buildUrl, encodeRef, parseJsonParam, repoPath, requestJson, requireToken } from './api';

export type RepoIdParams = {
    owner: string;
    repo: string;
};

function compactProtection(item: any) {
    if (!item) return item;
    return {
        url: item.url,
        required_status_checks: item.required_status_checks
            ? {
                  strict: item.required_status_checks.strict,
                  contexts: item.required_status_checks.contexts,
                  checks: item.required_status_checks.checks
              }
            : item.required_status_checks,
        enforce_admins: item.enforce_admins?.enabled ?? item.enforce_admins,
        required_pull_request_reviews: item.required_pull_request_reviews
            ? {
                  dismiss_stale_reviews: item.required_pull_request_reviews.dismiss_stale_reviews,
                  require_code_owner_reviews: item.required_pull_request_reviews.require_code_owner_reviews,
                  required_approving_review_count: item.required_pull_request_reviews.required_approving_review_count,
                  require_last_push_approval: item.required_pull_request_reviews.require_last_push_approval
              }
            : item.required_pull_request_reviews,
        restrictions: item.restrictions
            ? {
                  users: Array.isArray(item.restrictions.users) ? item.restrictions.users.map((u: any) => u.login || u) : [],
                  teams: Array.isArray(item.restrictions.teams) ? item.restrictions.teams.map((t: any) => t.slug || t.name || t) : [],
                  apps: Array.isArray(item.restrictions.apps) ? item.restrictions.apps.map((a: any) => a.slug || a.name || a) : []
              }
            : item.restrictions,
        required_linear_history: item.required_linear_history?.enabled ?? item.required_linear_history,
        allow_force_pushes: item.allow_force_pushes?.enabled ?? item.allow_force_pushes,
        allow_deletions: item.allow_deletions?.enabled ?? item.allow_deletions,
        block_creations: item.block_creations?.enabled ?? item.block_creations,
        required_conversation_resolution: item.required_conversation_resolution?.enabled ?? item.required_conversation_resolution,
        lock_branch: item.lock_branch?.enabled ?? item.lock_branch,
        allow_fork_syncing: item.allow_fork_syncing?.enabled ?? item.allow_fork_syncing
    };
}

export async function getBranchProtection(params: RepoIdParams & { branch: string }): Promise<any> {
    requireToken('get_branch_protection');
    const url = buildUrl(repoPath(params.owner, params.repo, `/branches/${encodeRef(params.branch)}/protection`));
    return compactProtection(await requestJson<any>({ method: 'GET', url }));
}

export async function updateBranchProtection(
    params: RepoIdParams & { branch: string; protection: any }
): Promise<any> {
    requireToken('update_branch_protection');
    const protection = parseJsonParam<Record<string, any>>(params.protection, 'protection');
    if (!protection || typeof protection !== 'object') {
        throw new Error('protection must be a JSON object matching PUT /branches/{branch}/protection');
    }
    const url = buildUrl(repoPath(params.owner, params.repo, `/branches/${encodeRef(params.branch)}/protection`));
    return compactProtection(
        await requestJson<any>({
            method: 'PUT',
            url,
            body: protection
        })
    );
}

export async function deleteBranchProtection(params: RepoIdParams & { branch: string }): Promise<any> {
    requireToken('delete_branch_protection');
    const url = buildUrl(repoPath(params.owner, params.repo, `/branches/${encodeRef(params.branch)}/protection`));
    const result = await requestJson<any>({ method: 'DELETE', url });
    return { ok: true, branch: params.branch, result };
}
