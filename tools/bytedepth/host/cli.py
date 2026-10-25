"""Privileged command boundary. Project quality never imports or calls this CLI."""
import argparse
import os
from pathlib import Path
import signal

from . import certificates, resources, runners, runtime
from .runtime import require


def main(argv):
    parser = argparse.ArgumentParser(prog='bytedepth host')
    actions = parser.add_subparsers(dest='action', required=True)
    for phase in ('integration', 'e2e'):
        command = actions.add_parser(phase)
        command.add_argument('--commit-sha', required=True, action='append')
    bootstrap = actions.add_parser('bootstrap')
    bootstrap.add_argument('--ensure', action='store_true')
    bootstrap.add_argument('--lock-held', action='store_true')
    provision = actions.add_parser('provision')
    provision.add_argument('--run-id', required=True)
    provision.add_argument('--manifest', type=Path, required=True)
    teardown = actions.add_parser('teardown')
    teardown.add_argument('--manifest', type=Path, required=True)
    certificate = actions.add_parser('certificate')
    certificate.add_argument('target', choices=('staging', 'legacy-edge'))
    args = parser.parse_args(argv)
    if args.action in ('integration', 'e2e'):
        require(len(args.commit_sha) == 1 and runtime.SHA.fullmatch(args.commit_sha[0]), 'Exactly one full --commit-sha is required')
    require(os.geteuid() == 0, 'Host operations require the signed Host Agent root task')
    # SIGTERM must unwind the same cleanup transaction as an ordinary failure.
    def interrupted(signum, frame):
        raise KeyboardInterrupt('Host task interrupted')
    signal.signal(signal.SIGTERM, interrupted)
    env = dict(os.environ)
    root = Path(env.get('RELEASE_PLATFORM_TEST_SOURCE_ROOT', '/opt/bytedepth'))
    if args.action == 'certificate':
        io = runtime.Host(root, env, native=False)
        certificates.provision(io, args.target)
        return
    env = runtime.test_configuration(env)
    io = runtime.Host(root, env)
    state = Path(env.get('RELEASE_PLATFORM_TEST_WORK_DIR', '/var/lib/bytedepth-staging'))
    if args.action in ('provision', 'teardown'):
        require(env.get('BYTEDEPTH_DEPLOY_MODE') == 'staging' and env.get('BYTEDEPTH_TEST_SLOT_LOCK_HELD') == '1', 'Staging mode and deployment-test lock are required')
        if args.action == 'provision':
            resources.provision(io, args.run_id, args.manifest)
        else:
            resources.teardown(io, args.manifest)
        return
    require(runtime.values(env.get('RELEASE_PLATFORM_TEST_CONFIG_FILE', '/etc/bytedepth-deploy.conf')).get('BYTEDEPTH_DEPLOY_MODE') == 'staging', 'BYTEDEPTH_DEPLOY_MODE must be staging')
    held = env.get('RELEASE_PLATFORM_TEST_LOCK_HELD') == '1' or (args.action == 'bootstrap' and args.lock_held)
    with runtime.lock(env.get('RELEASE_PLATFORM_TEST_LOCK_FILE', str(state / 'deployment-test.lock')), held):
        manifest = Path(env.get('RELEASE_PLATFORM_TEST_RUNTIME_MANIFEST', str(state / 'runtime/manifest')))
        if args.action == 'bootstrap':
            runtime.ensure(io, manifest, refresh=not args.ensure)
        else:
            runners.execute(io, args.commit_sha[0], args.action, state, Path(env.get('RELEASE_PLATFORM_TEST_EVIDENCE_DIR', str(state / 'test-history'))), manifest)
