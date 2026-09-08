import { buildUrl, compactCommit, parseJsonParam, repoPath, requestJson, requestText, requireToken, truncateText } from './api';

export type RepoIdParams = {
    owner: string;
    repo: string;
};

function compactWorkflow(item: any) {
    if (!item) return item;
    return {
        id: item.id,
        name: item.name,
        path: item.path,
        state: item.state,
        html_url: item.html_url,
        badge_url: item.badge_url,
        created_at: item.created_at,
        updated_at: item.updated_at
    };
}

function compactRun(item: any) {
    if (!item) return item;
    return {
        id: item.id,
        name: item.name,
        display_title: item.display_title,
        status: item.status,
        conclusion: item.conclusion,
        event: item.event,
        head_branch: item.head_branch,
        head_sha: item.head_sha,
        html_url: item.html_url,
        path: item.path,
        run_number: item.run_number,
        run_attempt: item.run_attempt,
        created_at: item.created_at,
        updated_at: item.updated_at,
        actor: item.actor?.login,
        triggering_actor: item.triggering_actor?.login
    };
}

function compactJob(item: any) {
    if (!item) return item;
    return {
        id: item.id,
        run_id: item.run_id,
        name: item.name,
        status: item.status,
        conclusion: item.conclusion,
        html_url: item.html_url,
        started_at: item.started_at,
        completed_at: item.completed_at,
        runner_name: item.runner_name,
        steps: Array.isArray(item.steps)
            ? item.steps.map((step: any) => ({
                  name: step.name,
                  status: step.status,
                  conclusion: step.conclusion,
                  number: step.number,
                  started_at: step.started_at,
                  completed_at: step.completed_at
              }))
            : []
    };
}

export async function listWorkflows(params: RepoIdParams & { page?: number; per_page?: number }): Promise<any> {
    const url = buildUrl(repoPath(params.owner, params.repo, '/actions/workflows'), {
        page: params.page ?? 1,
        per_page: params.per_page ?? 30
    });
    const data = await requestJson<any>({ method: 'GET', url });
    return {
        total_count: data?.total_count,
        workflows: Array.isArray(data?.workflows) ? data.workflows.map(compactWorkflow) : []
    };
}

export async function listWorkflowRuns(
    params: RepoIdParams & {
        workflow_id?: string | number;
        branch?: string;
        status?: string;
        event?: string;
        page?: number;
        per_page?: number;
    }
): Promise<any> {
    const suffix = params.workflow_id
        ? `/actions/workflows/${encodeURIComponent(String(params.workflow_id))}/runs`
        : '/actions/runs';
    const url = buildUrl(repoPath(params.owner, params.repo, suffix), {
        branch: params.branch,
        status: params.status,
        event: params.event,
        page: params.page ?? 1,
        per_page: params.per_page ?? 20
    });
    const data = await requestJson<any>({ method: 'GET', url });
    return {
        total_count: data?.total_count,
        workflow_runs: Array.isArray(data?.workflow_runs) ? data.workflow_runs.map(compactRun) : []
    };
}

export async function getWorkflowRun(params: RepoIdParams & { run_id: number | string }): Promise<any> {
    const url = buildUrl(repoPath(params.owner, params.repo, `/actions/runs/${encodeURIComponent(String(params.run_id))}`));
    return compactRun(await requestJson<any>({ method: 'GET', url }));
}

export async function triggerWorkflow(
    params: RepoIdParams & {
        workflow_id: string | number;
        ref: string;
        inputs?: any;
    }
): Promise<any> {
    requireToken('trigger_workflow');
    const url = buildUrl(
        repoPath(params.owner, params.repo, `/actions/workflows/${encodeURIComponent(String(params.workflow_id))}/dispatches`)
    );
    const inputs = parseJsonParam<Record<string, any>>(params.inputs, 'inputs');
    const result = await requestJson<any>({
        method: 'POST',
        url,
        body: {
            ref: params.ref,
            inputs
        }
    });
    return {
        ok: true,
        workflow_id: params.workflow_id,
        ref: params.ref,
        inputs: inputs || {},
        dispatch: result
    };
}

export async function getWorkflowJobs(
    params: RepoIdParams & {
        run_id: number | string;
        include_logs?: boolean;
        failed_only?: boolean;
        max_log_chars?: number;
        page?: number;
        per_page?: number;
    }
): Promise<any> {
    const url = buildUrl(repoPath(params.owner, params.repo, `/actions/runs/${encodeURIComponent(String(params.run_id))}/jobs`), {
        page: params.page ?? 1,
        per_page: params.per_page ?? 50
    });
    const data = await requestJson<any>({ method: 'GET', url });
    const jobs = Array.isArray(data?.jobs) ? data.jobs.map(compactJob) : [];
    const selected = params.failed_only
        ? jobs.filter((job: any) => job.conclusion && job.conclusion !== 'success' && job.conclusion !== 'skipped')
        : jobs;

    if (!params.include_logs) {
        return {
            total_count: data?.total_count,
            jobs: selected
        };
    }

    const maxLogChars = params.max_log_chars ?? 8000;
    const withLogs = [];
    for (const job of selected) {
        try {
            const logUrl = buildUrl(repoPath(params.owner, params.repo, `/actions/jobs/${encodeURIComponent(String(job.id))}/logs`));
            const logResp = await requestText({ method: 'GET', url: logUrl, timeoutMs: 60000 });
            const truncated = truncateText(logResp.text, maxLogChars);
            withLogs.push({
                ...job,
                logs: truncated.text,
                logs_truncated: truncated.truncated,
                logs_original_length: truncated.original_length
            });
        } catch (e: any) {
            withLogs.push({
                ...job,
                logs_error: String(e && e.message ? e.message : e)
            });
        }
    }

    return {
        total_count: data?.total_count,
        jobs: withLogs
    };
}

export async function rerunWorkflowRun(
    params: RepoIdParams & { run_id: number | string; failed_jobs_only?: boolean; enable_debug_logging?: boolean }
): Promise<any> {
    requireToken('rerun_workflow_run');
    const suffix = params.failed_jobs_only
        ? `/actions/runs/${encodeURIComponent(String(params.run_id))}/rerun-failed-jobs`
        : `/actions/runs/${encodeURIComponent(String(params.run_id))}/rerun`;
    const url = buildUrl(repoPath(params.owner, params.repo, suffix));
    const body = params.enable_debug_logging === undefined ? undefined : { enable_debug_logging: params.enable_debug_logging };
    const result = await requestJson<any>({ method: 'POST', url, body });
    return { ok: true, run_id: params.run_id, failed_jobs_only: Boolean(params.failed_jobs_only), result };
}

export async function cancelWorkflowRun(params: RepoIdParams & { run_id: number | string }): Promise<any> {
    requireToken('cancel_workflow_run');
    const url = buildUrl(repoPath(params.owner, params.repo, `/actions/runs/${encodeURIComponent(String(params.run_id))}/cancel`));
    const result = await requestJson<any>({ method: 'POST', url });
    return { ok: true, run_id: params.run_id, result };
}

export async function listCheckRuns(
    params: RepoIdParams & {
        ref: string;
        status?: string;
        filter?: string;
        page?: number;
        per_page?: number;
    }
): Promise<any> {
    const url = buildUrl(repoPath(params.owner, params.repo, `/commits/${encodeURIComponent(params.ref)}/check-runs`), {
        status: params.status,
        filter: params.filter,
        page: params.page ?? 1,
        per_page: params.per_page ?? 30
    });
    const data = await requestJson<any>({ method: 'GET', url });
    return {
        total_count: data?.total_count,
        check_runs: Array.isArray(data?.check_runs)
            ? data.check_runs.map((item: any) => ({
                  id: item.id,
                  name: item.name,
                  status: item.status,
                  conclusion: item.conclusion,
                  html_url: item.html_url,
                  details_url: item.details_url,
                  started_at: item.started_at,
                  completed_at: item.completed_at,
                  app: item.app?.slug || item.app?.name
              }))
            : []
    };
}

export { compactCommit };
