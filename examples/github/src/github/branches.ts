import { buildUrl, encodeRef, repoPath, requestJson, requireToken } from './api';
import { getRepository } from './repos';

export type RepoIdParams = {
    owner: string;
    repo: string;
};

export type CreateBranchParams = RepoIdParams & {
    new_branch: string;
    from_branch?: string;
    from_sha?: string;
};

export type DeleteBranchParams = RepoIdParams & {
    branch: string;
};

function requiredName(value: unknown, field: string): string {
    const name = String(value ?? '').trim();
    if (!name) {
        throw new Error(`${field} is required`);
    }
    return name;
}

async function getBranchHeadSha(params: RepoIdParams & { branch: string }): Promise<string> {
    const url = buildUrl(repoPath(params.owner, params.repo, `/git/ref/heads/${encodeRef(params.branch)}`));
    const data = await requestJson<any>({ method: 'GET', url });
    const sha = data?.object?.sha;
    if (!sha) {
        throw new Error(`Cannot resolve branch sha for ${params.branch}`);
    }
    return sha;
}

export async function createBranch(params: CreateBranchParams): Promise<any> {
    requireToken('create_branch');

    const newBranch = requiredName(params.new_branch, 'new_branch');
    const fromSha = String(params.from_sha ?? '').trim();
    let sha = fromSha;
    let fromBranch: string | undefined;

    if (!sha) {
        fromBranch =
            String(params.from_branch ?? '').trim() ||
            String((await getRepository({ owner: params.owner, repo: params.repo }))?.default_branch || 'main');
        sha = await getBranchHeadSha({ owner: params.owner, repo: params.repo, branch: fromBranch });
    }

    const url = buildUrl(repoPath(params.owner, params.repo, '/git/refs'));
    const result = await requestJson<any>({
        method: 'POST',
        url,
        body: {
            ref: `refs/heads/${newBranch}`,
            sha
        }
    });
    return {
        ...result,
        new_branch: newBranch,
        from_sha: sha,
        from_branch: fromBranch
    };
}

export async function deleteBranch(params: DeleteBranchParams): Promise<any> {
    requireToken('delete_branch');

    const branch = requiredName(params.branch, 'branch');
    const repoInfo = await getRepository({ owner: params.owner, repo: params.repo });
    const defaultBranch = String(repoInfo?.default_branch || '').trim();
    if (defaultBranch && branch === defaultBranch) {
        throw new Error(`Refusing to delete the default branch '${defaultBranch}'.`);
    }

    const url = buildUrl(repoPath(params.owner, params.repo, `/git/refs/heads/${encodeRef(branch)}`));
    const result = await requestJson<any>({ method: 'DELETE', url });
    return { ok: true, branch, result };
}
