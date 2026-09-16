import unittest
from python_code_patch import build_patch


class SourcePatchTest(unittest.TestCase):
    def test_uses_existing_container_without_replacing_image_or_environment(self):
        deployment = {"spec": {"template": {"spec": {"containers": [
            {"name": "weather-python-agent-existing", "image": "sam:installed", "env": [{"name": "SAM2_CHECKPOINT", "value": "/models/model.pt"}]}
        ]}}}}
        patch = build_patch(deployment, "python-agent-code-012345abcdef")
        container = patch["spec"]["template"]["spec"]["containers"][0]
        self.assertEqual(container["name"], "weather-python-agent-existing")
        self.assertNotIn("image", container)
        self.assertTrue(container["volumeMounts"][0]["readOnly"])
        self.assertEqual(container["env"][0]["name"], "AI_API_KEY")
        self.assertEqual(
            container["env"][0]["valueFrom"]["secretKeyRef"]["name"],
            "weather-secret",
        )

    def test_rejects_ambiguous_container_selection(self):
        with self.assertRaises(ValueError):
            build_patch({"spec": {"template": {"spec": {"containers": [{"name": "a"}, {"name": "b"}]}}}}, "python-agent-code-012345abcdef")


if __name__ == "__main__":
    unittest.main()
