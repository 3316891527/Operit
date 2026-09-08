import { buildUrl, repoPath, requestJson, requireToken } from './api';

export type RepoIdParams = {
    owner: string;
    repo: string;
};

export async function forkRepository(params: RepoIdParams & { organization?: string; name?: string; default_branch_only?: boolean }): Promise<any> {
    requireToken('fork_repository');
    const url = buildUrl(repoPath(params.owner, params.repo, '/forks'));
    const data = await requestJson<any>({
        method: 'POST',
        url,
        body: {
            organization: params.organization,
            name: params.name,
            default_branch_only: params.default_branch_only
        }
    });
    return {
        full_name: data.full_name,
        html_url: data.html_url,
        default_branch: data.default_branch,
        parent: data.parent?.full_name,
        source: data.source?.full_name,
        private: data.private
    };
}

export async function syncFork(params: RepoIdParams & { branch?: string }): Promise<any> {
    requireToken('sync_fork');
    const url = buildUrl(repoPath(params.owner, params.repo, '/merge-upstream'));
    const data = await requestJson<any>({
        method: 'POST',
        url,
        body: {
            branch: params.branch
        }
    });
    return {
        message: data.message,
        merge_type: data.merge_type,
        base_branch: data.base_branch
    };
}