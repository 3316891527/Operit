import { buildUrl, requestJson } from './api';

export async function searchCode(params: {
    query: string;
    sort?: string;
    order?: string;
    page?: number;
    per_page?: number;
}): Promise<any> {
    const url = buildUrl('/search/code', {
        q: params.query,
        sort: params.sort,
        order: params.order,
        page: params.page ?? 1,
        per_page: params.per_page ?? 20
    });
    const data = await requestJson<any>({
        method: 'GET',
        url,
        headers: { Accept: 'application/vnd.github.text-match+json' }
    });
    return {
        total_count: data?.total_count,
        incomplete_results: data?.incomplete_results,
        items: Array.isArray(data?.items)
            ? data.items.map((item: any) => ({
                  name: item.name,
                  path: item.path,
                  sha: item.sha,
                  html_url: item.html_url,
                  repository: item.repository?.full_name,
                  score: item.score,
                  text_matches: Array.isArray(item.text_matches)
                      ? item.text_matches.map((match: any) => ({
                            fragment: match.fragment,
                            property: match.property
                        }))
                      : []
              }))
            : []
    };
}

export async function searchIssues(params: {
    query: string;
    sort?: string;
    order?: string;
    page?: number;
    per_page?: number;
}): Promise<any> {
    const url = buildUrl('/search/issues', {
        q: params.query,
        sort: params.sort,
        order: params.order,
        page: params.page ?? 1,
        per_page: params.per_page ?? 20
    });
    const data = await requestJson<any>({ method: 'GET', url });
    return {
        total_count: data?.total_count,
        incomplete_results: data?.incomplete_results,
        items: Array.isArray(data?.items)
            ? data.items.map((item: any) => ({
                  number: item.number,
                  title: item.title,
                  state: item.state,
                  html_url: item.html_url,
                  repository: item.repository_url,
                  user: item.user?.login,
                  comments: item.comments,
                  pull_request: Boolean(item.pull_request),
                  updated_at: item.updated_at
              }))
            : []
    };
}