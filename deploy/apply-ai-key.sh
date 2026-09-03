#!/bin/sh
set -eu
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
PATH="/usr/local/bin:/usr/bin:$PATH"
export PATH

if [ ! -s /root/.weather-ai-key ]; then
  echo "missing /root/.weather-ai-key"
  exit 1
fi

python3 - <<'PY'
import json
from pathlib import Path
key = Path("/root/.weather-ai-key").read_text().strip()
Path("/tmp/ai-patch.json").write_text(json.dumps({"stringData": {"AI_API_KEY": key}}))
PY

kubectl -n weather patch secret weather-secret --type merge --patch-file /tmp/ai-patch.json
rm -f /tmp/ai-patch.json
kubectl -n weather rollout restart deploy/backend
kubectl -n weather rollout status deploy/backend --timeout=180s
wc -c /root/.weather-ai-key
