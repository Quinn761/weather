"""Run the camera-monitoring Roboflow workflow once from the command line."""
import os
import sys
from pathlib import Path

from inference_sdk import InferenceConfiguration, InferenceHTTPClient


def main() -> None:
    api_key = os.getenv("ROBOFLOW_API_KEY", "").strip()
    if not api_key:
        raise RuntimeError("ROBOFLOW_API_KEY environment variable is not configured")
    if len(sys.argv) != 2:
        raise RuntimeError("Usage: python run_roboflow_workflow.py PATH_TO_IMAGE.jpg")
    image = Path(sys.argv[1])
    if not image.is_file():
        raise FileNotFoundError(f"Image not found: {image}")
    client = InferenceHTTPClient(
        api_url="https://serverless.roboflow.com",
        api_key=api_key,
    ).configure(InferenceConfiguration(api_key_transport="header"))
    result = client.run_workflow(
        workspace_name="666s-workspace-l0hrj",
        workflow_id="1789542233524",
        images={"image": str(image)},
        use_cache=True,
    )
    print(result)


if __name__ == "__main__":
    main()
