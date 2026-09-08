import { buildUrl, compactCommit, compactFileChange, encodeRef, repoPath, requestJson, requestText, truncateText } from './api';

export type RepoIdParams = {
    owner: string;
    repo: string;
};

export async function listBranches(
    params: RepoIdParams & { protected_only?: boolean; page?: number; per_page?: number }
): Promise<any> {
    const url = buildUrl(repoPath(params.owner, params.repo, '/branches'), {
        protected: params.protected_only,
        page: params.page ?? 1,
        per_page: params.per_page ?? 30
    });
    const items = await requestJson<any[]>({ method: 'GET', url });
    return Array.isArray(items)
        ? items.map((item) => ({
              name: item.name,
              sha: item.commit?.sha,
              protected: item.protected,
              html_url: `https://github.com/${params.owner}/${params.repo}/tree/${item.name}`
          }))
        : [];
}

export async function listCommits(
    params: RepoIdParams & { sha?: string; path?: string; author?: string; page?: number; per_page?: number }
): Promise<any> {
    const url = buildUrl(repoPath(params.owner, params.repo, '/commits'), {
        sha: params.sha,
        path: params.path,
        author: params.author,
        page: params.page ?? 1,
        per_page: params.per_page ?? 20
    });
    const items = await requestJson<any[]>({ method: 'GET', url });
    return Array.isArray(items) ? items.map(compactCommit) : [];
}

export async function getCommit(params: RepoIdParams & { ref: string; include_files?: boolean }): Promise<any> {
    const url = buildUrl(repoPath(params.owner, params.repo, `/commits/${encodeURIComponent(params.ref)}`));
    const data = await requestJson<any>({ method: 'GET', url });
    return {
        ...compactCommit(data),
        stats: data.stats,
        files: params.include_files === false ? undefined : (Array.isArray(data.files) ? data.files.map((file: any) => compactFileChange(file, false)) : [])
    };
}

export async function compareRefs(
    params: RepoIdParams & { base: string; head: string; include_files?: boolean; include_patch?: boolean }
): Promise<any> {
    const spec = `${encodeRef(params.base)}...${encodeRef(params.head)}`;
    const url = buildUrl(repoPath(params.owner, params.repo, `/compare/${spec}`));
    const data = await requestJson<any>({ method: 'GET', url });
    const includeFiles = params.include_files !== false;
    return {
        status: data.status,
        ahead_by: data.ahead_by,
        behind_by: data.behind_by,
        total_commits: data.total_commits,
        html_url: data.html_url,
        permalink_url: data.permalink_url,
        base_commit: compactCommit(data.base_commit),
        merge_base_commit: compactCommit(data.merge_base_commit),
        commits: Array.isArray(data.commits) ? data.commits.map(compactCommit) : [],
        files: includeFiles
            ? Array.isArray(data.files)
                ? data.files.map((file: any) => compactFileChange(file, params.include_patch === true))
                : []
            : undefined
    };
}

export async function getCompareDiff(
    params: RepoIdParams & { base: string; head: string; max_chars?: number }
): Promise<any> {
    const spec = `${encodeRef(params.base)}...${encodeRef(params.head)}`;
    const url = buildUrl(repoPath(params.owner, params.repo, `/compare/${spec}`));
    const resp = await requestText({
        method: 'GET',
        url,
        headers: { Accept: 'application/vnd.github.diff' },
        timeoutMs: 60000
    });
    const truncated = truncateText(resp.text, params.max_chars ?? 20000);
    return {
        base: params.base,
        head: params.head,
        diff: truncated.text,
        truncated: truncated.truncated,
        original_length: truncated.original_length
    };
}