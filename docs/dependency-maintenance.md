# Dependency maintenance — October 25, 2026

Spring Boot remains on 3.5.16 and Java on 17. Compatible overrides select Jackson BOM 2.21.7, Commons Lang 3.18.0, Log4j 2.25.5, PostgreSQL JDBC 42.7.12, Tomcat 10.1.59, and test-only Commons Compress 1.26.0. These address the reported matches without a framework migration. Full Maven verification passes all 42 tests, including the six PostgreSQL cases. Five browser regressions pass, including stored-markup rendering and safe checkout retry after a lost successful response.

An [OSV batch query](https://google.github.io/osv.dev/api/) over 135 resolved artifact/version pairs on October 25 returned only these two remaining advisory IDs, both for Spring WebMVC 6.2.19:

| Advisory | Applicability in this project | Disposition |
| --- | --- | --- |
| [GHSA-j9f9-w8pj-32f8](https://github.com/advisories/GHSA-j9f9-w8pj-32f8) | Concerns SSE fragment streams. No SSE emitter or fragment-stream endpoint is configured. | Retain the version match, review if SSE is introduced. |
| [GHSA-pc63-qcmh-9cmg](https://github.com/advisories/GHSA-pc63-qcmh-9cmg) | Concerns XsltView resource-path limitations. No XSLT view or XML view resolver is configured. | Retain the version match, review if XSLT views are introduced. |

Maven Central lists no newer Spring 6.2 patch on the verification date; the advisory-fixed Spring 7 line would require a Boot 4 migration. That migration is outside this bounded local portfolio repair. These are documented matches in unused features, not demonstrated application exploits or a claim of security completeness. The project remains an unauthenticated local demo, without production hosting or real users.

OSV databases and package releases change. Rerun the resolved-graph check when updating dependencies; passing tests and dated advisory results are separate evidence.
