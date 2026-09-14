"""Mount versioned application source while retaining the installed SAM image."""
import json
import re
import sys


def build_patch(deployment, config_name):
    if not re.fullmatch(r"python-agent-code-[a-f0-9]{12}", config_name):
        raise ValueError("Invalid versioned source ConfigMap name")
    containers = deployment["spec"]["template"]["spec"]["containers"]
    if len(containers) != 1:
        raise ValueError("Expected a single Python Agent application container")
    return {"spec": {"template": {"spec": {
        "volumes": [{"name": "agent-source", "configMap": {"name": config_name}}],
        "containers": [{"name": containers[0]["name"], "volumeMounts": [
            {"name": "agent-source", "mountPath": "/app/app", "readOnly": True}
        ]}],
    }}}}


if __name__ == "__main__":
    print(json.dumps(build_patch(json.load(sys.stdin), sys.argv[1])))
