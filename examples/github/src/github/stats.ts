import { buildUrl, repoPath, requestRaw } from './api';

export type RepoIdParams = {
    owner: string;
    repo: string;
};

async function requestStats(url: string, kind: string): Promise<any> {
    const resp = await requestRaw({ method: 'GET', url });
    if (resp.statusCode === 202) {
        return {
            status: 'computing',
            retry: true,
            kind,
            statusCode: 202,
            message: 'GitHub is computing repository statistics. Retry shortly.'
        };
    }
    if (resp.statusCode === 204) {
        return {
            status: 'empty',
            kind,
            statusCode: 204,
            data: []
        };
    }
    const text = String(resp.content || '').trim();
    if (!text) {
        return { status: 'empty', kind, statusCode: resp.statusCode, data: [] };
    }
    try {
        return {
            status: 'ready',
            kind,
            statusCode: resp.statusCode,
            data: JSON.parse(text)
        };
    } catch (e) {
        throw new Error(`Failed to parse GitHub stats JSON (${resp.statusCode}): ${text.slice(0, 500)}`);
    }
}

export async function getContributorsStats(params: RepoIdParams): Promise<any> {
    const url = buildUrl(repoPath(params.owner, params.repo, '/stats/contributors'));
    const result = await requestStats(url, 'contributors');
    if (result.status !== 'ready' || !Array.isArray(result.data)) return result;
    return {
        ...result,
        data: result.data.map((item: any) => ({
            author: item.author?.login,
            total: item.total,
            weeks: item.weeks
        }))
    };
}

export async function getCommitActivity(params: RepoIdParams): Promise<any> {
    const url = buildUrl(repoPath(params.owner, params.repo, '/stats/commit_activity'));
    return requestStats(url, 'commit_activity');
}

export async function getCodeFrequency(params: RepoIdParams): Promise<any> {
    const url = buildUrl(repoPath(params.owner, params.repo, '/stats/code_frequency'));
    return requestStats(url, 'code_frequency');
}
