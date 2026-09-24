"""Update the single app image entry without requiring PyYAML on Jenkins."""
import re
import sys
from pathlib import Path
path, name, tag = sys.argv[1:]
if not re.fullmatch(r"[a-z0-9][a-z0-9._/-]*", name):
    raise SystemExit("Invalid Docker Hub image name")
if not re.fullmatch(r"[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}", tag):
    raise SystemExit("Invalid image tag")
p = Path(path)
text, count_name = re.subn(r"(?m)^(\s*newName:) .+$", lambda m: m[1] + " " + name, p.read_text())
text, count_tag = re.subn(r"(?m)^(\s*newTag:) .+$", lambda m: m[1] + " " + tag, text)
if count_name != 1 or count_tag != 1:
    raise SystemExit("Expected exactly one image in kustomization.yaml")
p.write_text(text)
