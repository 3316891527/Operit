import { buildUrl, repoPath, requestJson, requireToken } from './api';

export type RepoIdParams = {
    owner: string;
    repo: string;
};

function compactLabel(item: any) {
    if (!item) return item;
    return {
        id: item.id,
        name: item.name,
        color: item.color,
        description: item.description,
        default: item.default,
        url: item.url
    };
}

export async function listLabels(params: RepoIdParams & { page?: number; per_page?: number }): Promise<any> {
    const url = buildUrl(repoPath(params.owner, params.repo, '/labels'), {
        page: params.page ?? 1,
        per_page: params.per_page ?? 30
    });
    const items = await requestJson<any[]>({ method: 'GET', url });
    return Array.isArray(items) ? items.map(compactLabel) : [];
}

export async function getLabel(params: RepoIdParams & { name: string }): Promise<any> {
    const url = buildUrl(repoPath(params.owner, params.repo, `/labels/${encodeURIComponent(params.name)}`));
    return compactLabel(await requestJson<any>({ method: 'GET', url }));
}

export async function createLabel(
    params: RepoIdParams & { name: string; color: string; description?: string }
): Promise<any> {
    requireToken('create_label');
    const url = buildUrl(repoPath(params.owner, params.repo, '/labels'));
    return compactLabel(
        await requestJson<any>({
            method: 'POST',
            url,
            body: {
                name: params.name,
                color: String(params.color || '').replace(/^#/, ''),
                description: params.description
            }
        })
    );
}

export async function updateLabel(
    params: RepoIdParams & { name: string; new_name?: string; color?: string; description?: string }
): Promise<any> {
    requireToken('update_label');
    const url = buildUrl(repoPath(params.owner, params.repo, `/labels/${encodeURIComponent(params.name)}`));
    return compactLabel(
        await requestJson<any>({
            method: 'PATCH',
            url,
            body: {
                new_name: params.new_name,
                color: params.color ? String(params.color).replace(/^#/, '') : undefined,
                description: params.description
            }
        })
    );
}

export async function deleteLabel(params: RepoIdParams & { name: string }): Promise<any> {
    requireToken('delete_label');
    const url = buildUrl(repoPath(params.owner, params.repo, `/labels/${encodeURIComponent(params.name)}`));
    const result = await requestJson<any>({ method: 'DELETE', url });
    return { ok: true, name: params.name, result };
}
