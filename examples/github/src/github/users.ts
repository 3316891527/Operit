import { buildUrl, requestJson } from './api';

export async function getAuthenticatedUser(): Promise<any> {
    const data = await requestJson<any>({ method: 'GET', url: buildUrl('/user') });
    return {
        login: data.login,
        id: data.id,
        name: data.name,
        html_url: data.html_url,
        type: data.type,
        company: data.company,
        public_repos: data.public_repos,
        total_private_repos: data.total_private_repos,
        plan: data.plan?.name
    };
}

export async function getRateLimit(): Promise<any> {
    const data = await requestJson<any>({ method: 'GET', url: buildUrl('/rate_limit') });
    const core = data?.resources?.core || {};
    const search = data?.resources?.search || {};
    const graphql = data?.resources?.graphql || {};
    return {
        core: {
            limit: core.limit,
            remaining: core.remaining,
            reset: core.reset,
            used: core.used
        },
        search: {
            limit: search.limit,
            remaining: search.remaining,
            reset: search.reset,
            used: search.used
        },
        graphql: {
            limit: graphql.limit,
            remaining: graphql.remaining,
            reset: graphql.reset,
            used: graphql.used
        }
    };
}