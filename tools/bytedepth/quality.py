"""Local quality operations; Host execution is deliberately outside this module."""

import os
from pathlib import Path
import re

from . import ROOT
from .process import ToolError, capture


def java_home():
    candidates = [os.environ.get("JAVA_HOME_25_X64"), os.environ.get("JAVA_HOME")]
    if Path("/usr/libexec/java_home").is_file():
        candidates.append(capture(["/usr/libexec/java_home", "-v", "25"], ROOT).strip())
    for candidate in filter(None, candidates):
        executable = Path(candidate) / "bin/java"
        if executable.is_file() and re.search(r"(?:openjdk|java) 25(?:[.\s]|$)", capture([str(executable), "--version"], ROOT)):
            return str(candidate)
    raise ToolError("Java 25 is required; set JAVA_HOME_25_X64 or JAVA_HOME")
