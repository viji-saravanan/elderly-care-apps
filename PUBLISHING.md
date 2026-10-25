# Publishing this clean source tree to GitHub

The two Android projects are **folders of source files**, not ZIPs inside the repository.

1. On GitHub, create an **empty public repository** named `elderly-care-apps` under `viji-saravanan`. Do not add a GitHub-generated README, license, or `.gitignore` during creation.
2. Unpack this source-only archive into a local folder.
3. Open a terminal (Git Bash or PowerShell) **inside that unpacked folder** and run:

```bash
git init -b main
git add .
git status --short
git commit -m "Add sanitized Android phone and Wear OS source"
git remote add origin https://github.com/viji-saravanan/elderly-care-apps.git
git push -u origin main
```

Review `git status --short` before committing; ensure Firebase configs, build logs, local SDK paths, signing keys, and credentials are absent. Keep `elderlycarephone` and `elderlycarewatch` as folders; do not commit this archive as a single file.

**Before deploying:** set Firebase rules and app authentication for private health data, and rotate any existing credentials if they may already have been exposed elsewhere. The source scrub does not validate server-side security.
