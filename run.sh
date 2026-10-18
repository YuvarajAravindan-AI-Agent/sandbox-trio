#!/bin/bash
# Usage: ./run.sh  (reads ~/.trio.env: DEEPSEEK_API_KEY, TRIO_SANDBOX, optional DAYTONA_API_KEY)
set -a; . ${TRIO_ENV:-~/.trio.env}; set +a
exec java -jar ~/sandbox-trio/target/sandbox-trio-0.1.0.jar
