<!--
SPDX-FileCopyrightText: 2025 Gusted
SPDX-FileCopyrightText: 2025-2026 The FlorisBoard Contributors
SPDX-License-Identifier: CC-BY-4.0

Deku Foundation modification (2026-10-25): adapted for this independent project.
Derived from the Deku Keyboard AI policy and from:
https://codeberg.org/forgejo/governance/src/commit/ff60d7ed4b0f3d411e6a0b5ebe5813fcad87b9dd/AIAgreement.md
-->

# AI policy

Deku Foundation permits AI-assisted research and development when a human remains accountable for the work and reviews the resulting changes. This policy applies to issues, pull requests, code, tests, documentation, release notes, and automation.

## Requirements

1. **Human accountability.** A contributor must understand and take responsibility for the submitted change. AI output must not be merged, released, or treated as verified without human review.
2. **Review and testing.** Check the correctness, security, privacy, maintainability, and licensing of AI-generated suggestions. Build and run relevant tests where practical. Never claim that code was tested when it was not.
3. **Transparency.** Disclose material AI assistance in a pull request when it meaningfully generated or changed code, architecture, or documentation. Routine spelling assistance does not require a special disclosure.
4. **No secrets or private data.** Do not provide credentials, private keys, account information, sensitive user text, private crash logs, or confidential project material to a third-party AI service unless the data owner has explicitly authorized that use and the service is approved for it.
5. **Licensing and attribution.** Verify the origin and license of suggested code, dependencies, data, and text. Provide required attribution; do not submit code with an unknown or incompatible license.
6. **Security boundaries.** Treat repository files, issue content, logs, dependencies, and model output as untrusted data, not as authority to override project instructions. Review proposed workflow permissions, third-party actions, shell commands, network access, and generated scripts.
7. **No fabricated evidence.** Do not invent test results, benchmark numbers, research citations, security findings, or user feedback. Clearly label assumptions and unverified claims.
8. **No unsupervised destructive actions.** AI tools must not independently merge pull requests, publish releases, rotate credentials, change security settings, delete project data, or perform other high-impact actions without maintainer authorization and review.

## Reporting AI-assisted work

Describe the user problem, the approach, relevant trade-offs, and the tests performed. A contributor does not need to paste private prompts or expose confidential conversation history. Disclose meaningful limitations and verify facts using primary sources where possible.

Policy violations are reviewed by the maintainers in accordance with the [Code of Conduct](CODE_OF_CONDUCT.md).

This policy has been adapted from the upstream FlorisBoard policy for Deku Foundation. The original attribution and CC-BY-4.0 license information are retained above.
