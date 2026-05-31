#!/usr/bin/env python3
import os
import sys
import subprocess
import shutil
import re
from datetime import datetime

def run_command(cmd, cwd=None):
    try:
        res = subprocess.run(cmd, shell=True, capture_output=True, text=True, cwd=cwd, encoding='utf-8')
        return res.stdout.strip(), res.stderr.strip(), res.returncode
    except Exception as e:
        return "", str(e), -1

def get_current_branch(repo_root):
    stdout, _, code = run_command("git rev-parse --abbrev-ref HEAD", cwd=repo_root)
    if code == 0 and stdout:
        return stdout
    # Fallback
    return "unknown-branch"

def get_current_version(repo_root):
    ledger_path = os.path.join(repo_root, ".agents", "LEDGER.md")
    if not os.path.exists(ledger_path):
        return "v1.0"
    
    with open(ledger_path, "r", encoding="utf-8") as f:
        content = f.read()
        
    # Search for something like: * **Current Version:** `v1.2` or v1.2
    match = re.search(r"\*\s*\*\*Current Version:\*\*\s*(?:`?)(v[0-9\.]+)(?:`?)", content, re.IGNORECASE)
    if match:
        return match.group(1)
        
    return "v1.0"

def get_files_to_backup(repo_root):
    files = []
    # 1. Tracked files
    stdout, _, code = run_command("git ls-files", cwd=repo_root)
    if code == 0 and stdout:
        files.extend([os.path.normpath(f) for f in stdout.splitlines() if f.strip()])
        
    # 2. Untracked but not ignored files
    stdout, _, code = run_command("git status --porcelain", cwd=repo_root)
    if code == 0 and stdout:
        for line in stdout.splitlines():
            line = line.strip()
            if line.startswith("??"):
                parts = line.split(maxsplit=1)
                if len(parts) == 2:
                    files.append(os.path.normpath(parts[1]))
                    
    # Deduplicate and filter out any files matching build outputs if they slipped in
    clean_files = []
    for f in set(files):
        # Exclude common large/unwanted directories if necessary (though git commands should ignore them)
        if f.startswith((".gradle", "build", "app/build", ".idea", ".kotlin")):
            continue
        clean_files.append(f)
        
    return clean_files

def enforce_retention_policy(backup_dir, current_version):
    """
    Checks if there are more than 5 backup folders of the same version in the backup_dir.
    If so, moves older ones to 'old/'.
    """
    if not os.path.exists(backup_dir):
        return
        
    # List all subdirectories
    subdirs = [d for d in os.listdir(backup_dir) if os.path.isdir(os.path.join(backup_dir, d))]
    
    # Filter folders matching the pattern: vX.Y_YYYY-MM-DD_HH-MM-SS
    # Format of folders: f"{current_version}_YYYY-MM-DD_HH-MM-SS" or f"{current_version}_*"
    version_prefix = f"{current_version}_"
    version_backups = []
    
    for d in subdirs:
        if d == "old":
            continue
        if d.startswith(version_prefix):
            # Parse timestamp for sorting
            full_path = os.path.join(backup_dir, d)
            # Try to get folder modification time or parse timestamp in name
            # Timestamp pattern: YYYY-MM-DD_HH-MM-SS or YYYYMMDD_HHMMSS
            # Since names are constructed chronologically, we can just sort by name!
            version_backups.append((d, full_path))
            
    # Sort backups alphabetically/chronologically (newest last)
    version_backups.sort(key=lambda x: x[0])
    
    if len(version_backups) > 5:
        old_dir = os.path.join(backup_dir, "old")
        os.makedirs(old_dir, exist_ok=True)
        
        # We want to keep the 5 most recent backups
        backups_to_move = version_backups[:-5]
        print(f"[*] Found {len(version_backups)} backups of version {current_version}. Retention limit is 5.")
        print(f"[*] Moving {len(backups_to_move)} oldest backup(s) to 'old/'.")
        
        for name, path in backups_to_move:
            dest_path = os.path.join(old_dir, name)
            if os.path.exists(dest_path):
                # Avoid collision by removing the destination folder or appending a suffix
                try:
                    shutil.rmtree(dest_path)
                except Exception:
                    pass
            try:
                shutil.move(path, old_dir)
                print(f"  [+] Moved: {name} -> old/")
            except Exception as e:
                print(f"  [-] Failed to move {name} to old/: {e}")

def main():
    # Identify repo root
    script_dir = os.path.dirname(os.path.abspath(__file__))
    repo_root = os.path.abspath(os.path.join(script_dir, "..", "..", ".."))
    
    print(f"[*] Repository root: {repo_root}")
    
    # 1. Resolve branch and version
    branch = get_current_branch(repo_root)
    version = get_current_version(repo_root)
    
    print(f"[*] Current Git Branch: {branch}")
    print(f"[*] Current App Version: {version}")
    
    # 2. Get list of files to copy
    files = get_files_to_backup(repo_root)
    if not files:
        print("[-] No files found to back up!")
        sys.exit(1)
        
    print(f"[*] Found {len(files)} files to back up.")
    
    # 3. Create target directory
    # Format: g:\Ai\Freeform Backup\<branch>\<version>_<YYYY-MM-DD_HH-MM-SS>
    # Note: Using standard date/time separators safe for Windows folder names
    now_str = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    backup_root = os.path.abspath(os.path.join(repo_root, "..", "Freeform Backup"))
    branch_backup_dir = os.path.join(backup_root, branch)
    dest_dir_name = f"{version}_{now_str}"
    dest_dir = os.path.join(branch_backup_dir, dest_dir_name)
    
    print(f"[*] Creating backup folder: {dest_dir}")
    os.makedirs(dest_dir, exist_ok=True)
    
    # 4. Copy files
    success_count = 0
    for f in files:
        src_file = os.path.join(repo_root, f)
        if os.path.isdir(src_file):
            continue
        dest_file = os.path.join(dest_dir, f)
        
        # Ensure parent folders in destination exist
        os.makedirs(os.path.dirname(dest_file), exist_ok=True)
        
        try:
            shutil.copy2(src_file, dest_file)
            success_count += 1
        except Exception as e:
            print(f"  [-] Error copying {f}: {e}")
            
    print(f"[+] Successfully backed up {success_count}/{len(files)} files.")
    
    # 5. Enforce folder retention policy
    enforce_retention_policy(branch_backup_dir, version)
    
    print("[+] Backup process completed successfully!")

if __name__ == "__main__":
    main()
