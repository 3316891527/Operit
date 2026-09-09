import { buildUrl, repoPath, requestJson, requestRaw, requireToken } from './api';

export type RepoIdParams = {
    owner: string;
    repo: string;
};

function compactCollaborator(item: any) {
    if (!item) return item;
    return {
        login: item.login,
        id: item.id,
        type: item.type,
        html_url: item.html_url,
        role_name: item.role_name,
        permissions: item.permissions
    };
}

export async function listCollaborators(
    params: RepoIdParams & { affiliation?: string; permission?: string; page?: number; per_page?: number }
): Promise<any> {
    requireToken('list_collaborators');
    const url = buildUrl(repoPath(params.owner, params.repo, '/collaborators'), {
        affiliation: params.affiliation,
        permission: params.permission,
        page: params.page ?? 1,
        per_page: params.per_page ?? 30
    });
    const items = await requestJson<any[]>({ method: 'GET', url });
    return Array.isArray(items) ? items.map(compactCollaborator) : [];
}

export async function addCollaborator(
    params: RepoIdParams & { username: string; permission?: string }
): Promise<any> {
    requireToken('add_collaborator');
    const url = buildUrl(
        repoPath(params.owner, params.repo, `/collaborators/${encodeURIComponent(params.username)}`)
    );
    const data = await requestJson<any>({
        method: 'PUT',
        url,
        body: {
            permission: params.permission
        }
    });
    return {
        username: params.username,
        permission: params.permission || 'push',
        invited: Boolean(data && (data.id || data.html_url)),
        invitation: data && data.id
            ? {
                  id: data.id,
                  html_url: data.html_url,
                  permissions: data.permissions,
                  invitee: data.invitee?.login,
                  inviter: data.inviter?.login
              }
            : data
    };
}

export async function removeCollaborator(params: RepoIdParams & { username: string }): Promise<any> {
    requireToken('remove_collaborator');
    const url = buildUrl(
        repoPath(params.owner, params.repo, `/collaborators/${encodeURIComponent(params.username)}`)
    );
    const result = await requestJson<any>({ method: 'DELETE', url });
    return { ok: true, username: params.username, result };
}

export async function getCollaboratorPermission(params: RepoIdParams & { username: string }): Promise<any> {
    requireToken('get_collaborator_permission');
    const url = buildUrl(
        repoPath(params.owner, params.repo, `/collaborators/${encodeURIComponent(params.username)}/permission`)
    );
    const data = await requestJson<any>({ method: 'GET', url });
    return {
        permission: data.permission,
        role_name: data.role_name,
        user: data.user?.login
    };
}

export async function checkCollaborator(params: RepoIdParams & { username: string }): Promise<any> {
    requireToken('check_collaborator');
    const url = buildUrl(
        repoPath(params.owner, params.repo, `/collaborators/${encodeURIComponent(params.username)}`)
    );
    try {
        const resp = await requestRaw({ method: 'GET', url });
        return { username: params.username, is_collaborator: resp.statusCode === 204 || resp.statusCode === 200 };
    } catch (e: any) {
        const message = String(e && e.message ? e.message : e);
        if (message.includes('404')) {
            return { username: params.username, is_collaborator: false };
        }
        throw e;
    }
}
