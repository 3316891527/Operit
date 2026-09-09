import { buildUrl, parseBool, parseJsonParam, repoPath, requestJson, requireToken, splitCsv } from './api';

export type RepoIdParams = {
    owner: string;
    repo: string;
};

function compactWebhook(item: any) {
    if (!item) return item;
    const config = item.config || {};
    return {
        id: item.id,
        name: item.name,
        active: item.active,
        events: item.events,
        config: {
            url: config.url,
            content_type: config.content_type,
            insecure_ssl: config.insecure_ssl
        },
        created_at: item.created_at,
        updated_at: item.updated_at,
        ping_url: item.ping_url,
        test_url: item.test_url,
        last_response: item.last_response
    };
}

export async function listWebhooks(params: RepoIdParams & { page?: number; per_page?: number }): Promise<any> {
    requireToken('list_webhooks');
    const url = buildUrl(repoPath(params.owner, params.repo, '/hooks'), {
        page: params.page ?? 1,
        per_page: params.per_page ?? 30
    });
    const items = await requestJson<any[]>({ method: 'GET', url });
    return Array.isArray(items) ? items.map(compactWebhook) : [];
}

export async function getWebhook(params: RepoIdParams & { hook_id: number | string }): Promise<any> {
    requireToken('get_webhook');
    const url = buildUrl(repoPath(params.owner, params.repo, `/hooks/${encodeURIComponent(String(params.hook_id))}`));
    return compactWebhook(await requestJson<any>({ method: 'GET', url }));
}

export async function createWebhook(
    params: RepoIdParams & {
        url: string;
        content_type?: string;
        secret?: string;
        insecure_ssl?: boolean | string;
        events?: string | string[];
        active?: boolean | string;
        config?: any;
    }
): Promise<any> {
    requireToken('create_webhook');
    const parsedConfig = parseJsonParam<Record<string, any>>(params.config, 'config') || {};
    const insecure = parseBool(params.insecure_ssl);
    const url = buildUrl(repoPath(params.owner, params.repo, '/hooks'));
    return compactWebhook(
        await requestJson<any>({
            method: 'POST',
            url,
            body: {
                name: 'web',
                active: parseBool(params.active),
                events: splitCsv(params.events),
                config: {
                    url: params.url || parsedConfig.url,
                    content_type: params.content_type || parsedConfig.content_type || 'json',
                    secret: params.secret || parsedConfig.secret,
                    insecure_ssl: insecure === undefined ? parsedConfig.insecure_ssl : insecure ? '1' : '0'
                }
            }
        })
    );
}

export async function updateWebhook(
    params: RepoIdParams & {
        hook_id: number | string;
        url?: string;
        content_type?: string;
        secret?: string;
        insecure_ssl?: boolean | string;
        events?: string | string[];
        add_events?: string | string[];
        remove_events?: string | string[];
        active?: boolean | string;
        config?: any;
    }
): Promise<any> {
    requireToken('update_webhook');
    const parsedConfig = parseJsonParam<Record<string, any>>(params.config, 'config') || {};
    const insecure = parseBool(params.insecure_ssl);
    const config: Record<string, any> = { ...parsedConfig };
    if (params.url) config.url = params.url;
    if (params.content_type) config.content_type = params.content_type;
    if (params.secret) config.secret = params.secret;
    if (insecure !== undefined) config.insecure_ssl = insecure ? '1' : '0';

    const url = buildUrl(repoPath(params.owner, params.repo, `/hooks/${encodeURIComponent(String(params.hook_id))}`));
    return compactWebhook(
        await requestJson<any>({
            method: 'PATCH',
            url,
            body: {
                active: parseBool(params.active),
                events: splitCsv(params.events),
                add_events: splitCsv(params.add_events),
                remove_events: splitCsv(params.remove_events),
                config: Object.keys(config).length > 0 ? config : undefined
            }
        })
    );
}

export async function deleteWebhook(params: RepoIdParams & { hook_id: number | string }): Promise<any> {
    requireToken('delete_webhook');
    const url = buildUrl(repoPath(params.owner, params.repo, `/hooks/${encodeURIComponent(String(params.hook_id))}`));
    const result = await requestJson<any>({ method: 'DELETE', url });
    return { ok: true, hook_id: params.hook_id, result };
}

export async function pingWebhook(params: RepoIdParams & { hook_id: number | string }): Promise<any> {
    requireToken('ping_webhook');
    const url = buildUrl(repoPath(params.owner, params.repo, `/hooks/${encodeURIComponent(String(params.hook_id))}/pings`));
    const result = await requestJson<any>({ method: 'POST', url });
    return { ok: true, hook_id: params.hook_id, result };
}
