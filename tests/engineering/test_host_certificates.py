"""Certificate helpers never operate real Certbot/Nginx in these tests."""
from pathlib import Path
import unittest
from unittest.mock import Mock
from tools.bytedepth.process import ToolError
from tools.bytedepth.host import certificates


class CertificateTests(unittest.TestCase):
    def test_certificate_requires_exact_san_expiry_and_matching_key(self):
        io = Mock(root=Path('.'))
        io.call.side_effect = ['X509v3 Subject Alternative Name:\n DNS:staging-bytedepth.bytedepth.cn', '', 'PUBLIC KEY', 'NORMAL KEY', 'NORMAL KEY']
        certificates.validate(io, 'staging-bytedepth.bytedepth.cn')
        self.assertIn('-checkend', io.call.call_args_list[1].args[0])
        for replies in [['DNS:other.example'], ['DNS:staging-bytedepth.bytedepth.cn', ToolError('expired')], ['DNS:staging-bytedepth.bytedepth.cn', '', 'PUBLIC KEY', 'NORMAL KEY', 'DIFFERENT']]:
            io.call.side_effect = replies
            with self.assertRaises(ToolError):
                certificates.validate(io, 'staging-bytedepth.bytedepth.cn')

    def test_certificate_adapter_resolves_versioned_assets_from_its_checkout(self):
        import os
        from unittest.mock import patch
        from tools.bytedepth import ROOT
        from tools.bytedepth.host import cli
        with patch.dict(os.environ, {}, clear=True), patch.object(os, 'geteuid', return_value=0), patch.object(cli.signal, 'signal'), patch.object(cli.runtime, 'Host') as host, patch.object(cli.certificates, 'provision'):
            cli.main(['certificate', 'staging'])
        self.assertEqual(ROOT, host.call_args.args[0])
