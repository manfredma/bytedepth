"""验证仓库约束及隔离的交接文档失败夹具。"""

from pathlib import Path
import re
import tempfile
import unittest
import xml.etree.ElementTree as ET

from tools.bytedepth import ROOT

DOCUMENT_RULES = {'AGENTS.md': ['默认推进到 staging 部署及验证完成、需要人工验收的位置', '#任务追踪与范围变更', '#诊断与重试'],
 'docs/agent-guides/agent-responsibility-boundary.md': ['默认授权 Agent 在本任务范围内提交、推送分支、创建或更新 PR',
                                                        '**默认（需要人工验收）**',
                                                        '**明确免人工验收**',
                                                        '用户对本任务明确说',
                                                        '该授权不自动延续到其他任务',
                                                        '授权依据必须记录为用户的免验收指令，不能伪称用户已查看或手工验收',
                                                        '“继续”或没有回复不等于免人工验收授权',
                                                        '生产验证成功的终态',
                                                        '免人工验收只免除等待人工确认，不免除质量门禁',
                                                        '**真实阻塞**',
                                                        '**明确限制范围**',
                                                        'CI/CD 是构建、部署和验证的执行工具，不是 Agent 开发流程的授权来源',
                                                        '单步且可当场验证的修改无需另建清单',
                                                        '用户目标、可观察的验收条件、当前状态、证据位置',
                                                        '上下文压缩后，先读取清单和最新用户指令',
                                                        '追加本轮、替换旧要求或延后',
                                                        '#诊断与重试',
                                                        '## 主线优先与 backlog',
                                                        '阻止哪一步、实际证据、最小解除动作',
                                                        '未知项先安排最小验证，不直接宣称阻塞',
                                                        '优先验证现有运行时和工具链',
                                                        '主流程跑通后再处理 backlog',
                                                        '已投入的时间、已批准的方案或局部测试通过都不能证明剩余工作仍属主线'],
 'docs/engineering/gotchas.md': ['当前假设、支持证据、反证、已排除方向', '没有新证据时停止重复执行同一方法', '#任务追踪与范围变更', '状态不确定'],
 'docs/README.md': ['#主线优先与-backlog'],
 'deploy/README.md': ['release-platform', 'systemd'],
 'docs/releases/README.md': ['完整 commit SHA']}

REMOVED_RELEASE = re.compile(r'deploy/(?:deploy-staging|deploy-production|deploy-production-remote)\.sh|scripts/verify-production-release\.sh')


def handoff(root):
    for name in ['AGENTS.md', 'deploy/README.md', 'docs/agent-guides/maven.md', 'docs/releases/README.md', 'docs/engineering/gotchas.md', 'docs/engineering/release-platform-only.md']:
        assert (root / name).is_file(), name
    deploy = (root / 'deploy/README.md').read_text()
    assert 'release-platform' in deploy and 'systemd' in deploy
    assert '完整 commit SHA' in (root / 'docs/releases/README.md').read_text()
    assert '状态不确定' in (root / 'docs/engineering/gotchas.md').read_text()
    for name in ['deploy/README.md', 'docs/engineering/release-platform-only.md', 'docs/releases/README.md']:
        text = (root / name).read_text()
        assert not REMOVED_RELEASE.search(text), name
        assert not re.search(r'docker compose|docker-compose|docker restart|docker run|compose up', text, re.I), name


class RepositoryTests(unittest.TestCase):
    def test_agent_delivery_and_retry_rules_and_navigation_remain(self):
        for name, rules in DOCUMENT_RULES.items():
            for rule in rules:
                with self.subTest(file=name, rule=rule):
                    self.assertIn(rule, (ROOT / name).read_text())
        for name in ['docs/README.md', 'docs/engineering/git-workflow.md', 'docs/engineering/unified-release-pipeline.md', 'docs/engineering/release-platform-only.md', 'docs/releases/README.md', 'docs/architecture/decisions/0022-pr-candidate-staging-before-merge.md']:
            self.assertIn('agent-responsibility-boundary.md', (ROOT / name).read_text())

    def test_handoff_docs_refuse_missing_sha_and_obsolete_runtime(self):
        handoff(ROOT)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            files = {'AGENTS.md': 'release-platform', 'deploy/README.md': 'release-platform systemd', 'docs/agent-guides/maven.md': 'Java 25', 'docs/releases/README.md': '完整 commit SHA', 'docs/engineering/gotchas.md': '状态不确定', 'docs/engineering/release-platform-only.md': 'release-platform'}
            for name, text in files.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text)
            handoff(root)
            (root / 'AGENTS.md').write_text('历史主机 124.221.143.25，不是当前环境绑定')
            handoff(root)
            release = root / 'docs/releases/README.md'
            release.write_text('No SHA')
            with self.assertRaises(AssertionError):
                handoff(root)
            release.write_text('完整 commit SHA')
            for forbidden in ['deploy/deploy-staging.sh', 'docker run obsolete']:
                (root / 'deploy/README.md').write_text('release-platform systemd ' + forbidden)
                with self.subTest(forbidden=forbidden), self.assertRaises(AssertionError):
                    handoff(root)

    def test_platform_only_release_boundary(self):
        for path in ['deploy/deploy-staging.sh', 'deploy/deploy-production.sh', 'deploy/deploy-production-remote.sh', 'scripts/verify-production-release.sh']:
            self.assertFalse((ROOT / path).exists(), path)
        agents = (ROOT / 'AGENTS.md').read_text()
        for text in ['AI Agent', 'Host Agent', 'canonical release-platform']:
            self.assertIn(text, agents)
        self.assertIn('不允许从项目工作区直接发布', (ROOT / 'deploy/README.md').read_text())
        releases = (ROOT / 'docs/releases/README.md').read_text()
        self.assertIn('Agent 使用 canonical API', releases)
        self.assertNotRegex(releases, '触发候选任务只允许通过 release-platform 页面|发布.*必须在 release-platform 页面完成')
        for base in ['docs/engineering', 'docs/releases', 'docs/security']:
            for path in (ROOT / base).rglob('*.md'):
                self.assertNotRegex(path.read_text(), REMOVED_RELEASE, str(path))

    def test_pinned_maven_and_formatter_configuration(self):
        self.assertTrue((ROOT / 'mvnw').stat().st_mode & 0o111)
        properties = (ROOT / '.mvn/wrapper/maven-wrapper.properties').read_text()
        self.assertIn('distributionUrl=https://maven.aliyun.com/repository/public/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip', properties)
        jvm = (ROOT / '.mvn/jvm.config').read_text()
        for flag in ['--enable-native-access=ALL-UNNAMED', '--sun-misc-unsafe-memory-access=allow', '-Xshare:off']:
            self.assertIn(flag, jvm.splitlines())
        pom = (ROOT / 'pom.xml').read_text()
        for rule in ['spotless-maven-plugin', 'palantirJavaFormat', 'palantir-java-format.version']:
            self.assertIn(rule, pom)
        for name in ['.editorconfig', '.prettierrc.json', '.prettierignore']:
            self.assertTrue((ROOT / name).is_file())

    def test_hearth_identity_does_not_grant_application_authorities(self):
        base = ROOT / 'bytedepth-adapter/src/main/java'
        security = base / 'manfred/bytedepth/adapter/web/security/HearthOidcSecurityConfiguration.java'
        for text in ['OAuth2AuthorizationRequestCustomizers.withPkce', 'OidcClientInitiatedLogoutSuccessHandler']:
            self.assertIn(text, security.read_text())
        self.assertIn('spring-boot-starter-oauth2-client', (ROOT / 'bytedepth-adapter/pom.xml').read_text())
        config = (ROOT / 'bytedepth-start/src/main/resources/application.yml').read_text()
        for text in ['BYTEDEPTH_HEARTH_OIDC_ISSUER', 'BYTEDEPTH_HEARTH_OIDC_CLIENT_SECRET']:
            self.assertIn(text, config)
        migration = (ROOT / 'bytedepth-start/src/main/resources/db/migration/V30__add_hearth_oidc_identity.sql').read_text()
        for text in ['user_identity', 'UNIQUE (issuer, subject)']:
            self.assertIn(text, migration)
        doc = (ROOT / 'docs/security/authentication.md').read_text()
        self.assertIn('OIDC', doc)
        self.assertIn('iss + sub', doc)
        for java in base.rglob('*.java'):
            self.assertNotRegex(java.read_text(), r'role_key.*SimpleGrantedAuthority|preferred_username.*SimpleGrantedAuthority|claim.*permission')

    def test_flyway_historical_warning_safety(self):
        migrations = ROOT / 'bytedepth-start/src/main/resources/db/migration'
        v8 = (migrations / 'V8__add_more_categories.sql').read_text()
        self.assertNotIn('INSERT IGNORE', v8)
        self.assertIn('INSERT INTO `category` (name, slug, parent_id) VALUES', v8)
        self.assertIn("    ('开发框架', 'framework', NULL);", v8)
        for name in ['V10__account_rbac.sql', 'V22__add_annotation_deleted_flag.sql']:
            self.assertNotIn('TINYINT(1)', (migrations / name).read_text())

    def test_view_log_archive_constraints(self):
        mapper = ROOT / 'bytedepth-infrastructure/src/main/resources/mapper/ViewLogArchiveMapper.xml'
        xml = mapper.read_text()
        self.assertLess(xml.index('id="insertExactHourlyAggregates"'), xml.index('id="deleteBucket"'))
        self.assertLess(xml.index('id="incrementHourlyAggregates"'), xml.index('id="deleteBucket"'))
        base = 'bytedepth-infrastructure/src/main/java/manfred/bytedepth/infrastructure/stats/'
        required = {'PostViewLogMapper.java': ['@Param("cutoff")'], 'ViewLogArchiveJob.java': ['fixedDelayString', 'runWithLock'], 'ViewLogArchiveAdapter.java': ['GET_LOCK', 'bytedepth:view-log-archive'], 'ViewLogTablespaceMaintenanceJob.java': ['optimizeLockName'], 'ViewLogTablespaceMaintenanceProperties.java': ['@ConfigurationProperties(prefix = "bytedepth.analytics")', '@ConstructorBinding']}
        for name, values in required.items():
            for text in values:
                self.assertIn(text, (ROOT / (base + name)).read_text())
        self.assertTrue((ROOT / 'bytedepth-start/src/main/resources/db/migration/V25__add_view_log_archive_stats.sql').is_file())
        config = (ROOT / 'bytedepth-start/src/main/resources/application.yml').read_text()
        changelog = (ROOT / 'docs/releases/CHANGELOG.md').read_text()
        self.assertIn('bytedepth:view-log-tablespace', config + changelog)
        for text in ['## Unreleased', '### Changed', '最近 7 天']:
            self.assertIn(text, changelog)
        self.assertNotIn('待下一版本开发内容', changelog)

    def test_integration_profile_keeps_external_resources_and_bounded_test_context(self):
        pom = ET.parse(ROOT / 'pom.xml').getroot()
        namespace = {'m': pom.tag.partition('}')[0].lstrip('{')}
        profile = next(p for p in pom.findall('m:profiles/m:profile', namespace) if p.findtext('m:id', namespaces=namespace) == 'staging-integration')
        text = ET.tostring(profile, encoding='unicode')
        for required in ['${env.SPRING_PROFILES_ACTIVE}', 'maven-surefire-plugin', 'maven-failsafe-plugin', '**/*IT.java', '${argLine} -Xmx512m']:
            self.assertIn(required, text)
        self.assertEqual('16', profile.findtext('.//m:spring.test.context.cache.maxSize', namespaces=namespace))
        self.assertEqual('true', profile.findtext('.//m:skipTests', namespaces=namespace))
        self.assertNotRegex(text, r'bytedepth\.it\.(manifest|redis)|BYTEDEPTH_STAGING_IT_REDIS_PASSWORD')
        paths = ['bytedepth-start/src/test/java/manfred/bytedepth/integration/' + name for name in ['PostRepositoryIT.java', 'AnnotationContentUpdateIT.java', 'AnnotationITSupport.java', 'AnnotationRevisionTransactionIT.java']]
        paths.append('bytedepth-infrastructure/src/test/java/manfred/bytedepth/infrastructure/ratelimit/RedisRateLimitAdapterIT.java')
        for name in paths:
            source = (ROOT / name).read_text()
            self.assertNotRegex(source, r'(?i)docker|testcontainers|mysqlcontainer|serviceconnection|jdbc:mysql://|spring\.datasource|localhost:3306')
            if name.endswith(('AnnotationContentUpdateIT.java', 'AnnotationRevisionTransactionIT.java')):
                self.assertIn('extends AnnotationITSupport', source)
        for name in ['pom.xml', 'bytedepth-start/pom.xml']:
            self.assertNotIn('testcontainers', (ROOT / name).read_text().lower())
        self.assertFalse((ROOT / 'bytedepth-start/src/test/resources/testcontainers.properties').exists())

    def test_current_gotchas_links_resolve_to_real_source_or_docs(self):
        source = ROOT / 'docs/engineering/gotchas.md'
        for link in re.findall(r'\]\(([^)]+)\)', source.read_text()):
            if link.startswith(('https:', 'http:', '#')):
                continue
            self.assertTrue((source.parent / link.split('#')[0]).exists(), link)

    def test_operation_entrypoints_have_one_node_dispatcher(self):
        for name in ['deploy/provision-staging-certificate.sh', 'deploy/provision-production-edge-staging-certificate.sh']:
            self.assertFalse((ROOT / name).exists(), name)
        self.assertTrue((ROOT / 'tools/node.mjs').is_file())
        expected = {'tools/bytedepth/__main__.py', 'tools/node.mjs', 'scripts/run-local-quality.sh', 'scripts/format-code.sh', 'scripts/format-check.sh', 'scripts/run-mobile-quality.sh', 'scripts/mobile-staging-fixtures.mjs', 'deploy/run-staging-integration-tests.sh', 'deploy/run-staging-e2e-tests.sh', 'deploy/nginx/reload-nginx-deploy-hook.sh'}
        actual = {'tools/bytedepth/__main__.py', 'tools/node.mjs'}
        for directory in ['scripts', 'deploy']:
            for path in (ROOT / directory).rglob('*'):
                if path.suffix in {'.sh', '.mjs', '.py'} and 'test-fixtures' not in path.parts:
                    actual.add(path.relative_to(ROOT).as_posix())
        self.assertEqual(expected, actual)
        self.assertEqual(10, len(actual))
        for name in ['tools/assets.mjs', 'apps/mobile/tools/assets.mjs', 'apps/mobile/tools/checks.mjs', 'apps/mobile/tools/delivery.mjs', 'apps/mobile/tools/fixtures.mjs']:
            text = (ROOT / name).read_text()
            self.assertNotIn('process.argv', text, name)
            self.assertNotIn('#!/usr/bin/env node', text, name)

    def test_python_script_explanations_use_chinese(self):
        import ast
        import io
        import tokenize
        for directory in ['tools', 'tests/engineering']:
            for path in (ROOT / directory).rglob('*.py'):
                source = path.read_text()
                explanations = [token.string for token in tokenize.generate_tokens(io.StringIO(source).readline) if token.type == tokenize.COMMENT and not token.string.startswith('#!')]
                for node in ast.walk(ast.parse(source)):
                    if isinstance(node, (ast.Module, ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef)):
                        doc = ast.get_docstring(node)
                        if doc:
                            explanations.append(doc)
                for text in explanations:
                    self.assertRegex(text, r'[\u3400-\u9fff]', str(path))
