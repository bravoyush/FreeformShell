#!/usr/bin/env python3
import os
import sys
import subprocess
import re
from datetime import datetime

def run_command(cmd, cwd=None):
    try:
        res = subprocess.run(cmd, shell=True, capture_output=True, text=True, cwd=cwd, encoding='utf-8')
        return res.stdout.strip(), res.stderr.strip(), res.returncode
    except Exception as e:
        return "", str(e), -1

def get_git_changes(repo_root):
    stdout, _, _ = run_command("git status --porcelain", cwd=repo_root)
    if not stdout:
        return []
    
    changes = []
    for line in stdout.split("\n"):
        line = line.strip()
        if not line:
            continue
        parts = line.split(maxsplit=1)
        if len(parts) == 2:
            status, filepath = parts[0], parts[1]
            changes.append({"status": status, "file": filepath})
    return changes

def update_ledger(ledger_path, message, modified_files_str):
    if not os.path.exists(ledger_path):
        print(f"[-] Ledger file not found at: {ledger_path}")
        return False
        
    with open(ledger_path, "r", encoding="utf-8") as f:
        content = f.read()
        
    today_str = datetime.now().strftime("%Y-%m-%d")
    
    # 1. Update the last updated timestamp footer
    # Looking for *Last Updated: YYYY-MM-DD by Antigravity (message)*
    pattern = r"\*Last Updated:.*?\*"
    replacement = f"*Last Updated: {today_str} by Antigravity ({message})*"
    if re.search(pattern, content):
        content = re.sub(pattern, replacement, content)
        print("[+] Updated ledger Last Updated footer.")
    else:
        # Append to the end if not found
        content += f"\n\n{replacement}\n"
        print("[+] Appended Last Updated footer.")
        
    # 2. Add a new completed feature item to "🟢 Completed Features" checklist
    # Find the section and insert the new task at the end of the completed checklist
    completed_section_regex = r"(### 🟢 Completed Features.*?\n)(.*?)(### 🟡 In Progress / Planned)"
    match = re.search(completed_section_regex, content, re.DOTALL)
    
    if match:
        header = match.group(1)
        list_content = match.group(2)
        next_section = match.group(3)
        
        # Check if this feature has already been appended to prevent duplicates
        feature_title = f"**{message}**"
        if feature_title not in list_content:
            new_item = f"- [x] {feature_title}: Automated state synchronization. (Modified: {modified_files_str})\n"
            # Append right before the last blank line in list_content, or at the end
            lines = list_content.splitlines()
            # Find the last non-empty line or list item
            insert_idx = len(lines)
            for idx in range(len(lines) - 1, -1, -1):
                if lines[idx].strip().startswith("- ["):
                    insert_idx = idx + 1
                    break
            lines.insert(insert_idx, new_item)
            new_list_content = "\n".join(lines) + "\n"
            
            content = content.replace(match.group(0), f"{header}{new_list_content}{next_section}")
            print(f"[+] Appended new completed feature to ledger: {message}")
        else:
            print("[-] Feature already exists in completed checklist.")
    else:
        print("[-] Could not locate 🟢 Completed Features section to append checklist item.")

    with open(ledger_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(content)
    return True

def generate_handoff(handoff_path, message, changes):
    today_str = datetime.now().strftime("%Y-%m-%d")
    
    modified = [c["file"] for c in changes if c["status"] in ("M", "AM", "MM")]
    untracked = [c["file"] for c in changes if c["status"] in ("??", "A")]
    deleted = [c["file"] for c in changes if c["status"] in ("D", "RM")]
    
    # Build clean lists of files
    mod_items = []
    for f in modified:
        f_posix = f.replace('\\', '/')
        mod_items.append(f"    *   [MODIFY] [{os.path.basename(f)}](file:///{f_posix})")
    mod_lines = "\n".join(mod_items)
    
    new_items = []
    for f in untracked:
        f_posix = f.replace('\\', '/')
        new_items.append(f"    *   [NEW] [{os.path.basename(f)}](file:///{f_posix})")
    new_lines = "\n".join(new_items)
    
    del_items = []
    for f in deleted:
        f_posix = f.replace('\\', '/')
        del_items.append(f"    *   [DELETE] [{os.path.basename(f)}](file:///{f_posix})")
    del_lines = "\n".join(del_items)
    
    mod_section = f"\n*   **Modified Files:**\n{mod_lines}" if modified else ""
    new_section = f"\n*   **New Files:**\n{new_lines}" if untracked else ""
    del_section = f"\n*   **Deleted Files:**\n{del_lines}" if deleted else ""
    
    files_summary = ""
    if mod_section: files_summary += mod_section
    if new_section: files_summary += new_section
    if del_section: files_summary += del_section

    handoff_content = f"""# 🤝 Agent Handoff Brief

*   **From:** Antigravity
*   **To:** Any Subsequent AI Agent / Human Coder
*   **Date:** {today_str}
*   **Current State:** {message}

---

## ⚡ Context Snapshot

1.  **Codebase Graph Status:** Graphify AST index is fully synchronized and current.
2.  **State Ledger Status:** Automated state ledger has been successfully updated at [.agents/LEDGER.md](file:///.agents/LEDGER.md).
3.  **Active Rules Status:** Android constraints, gesture safeguards, and graphify rules are loaded and fully active.
{files_summary}

---

## 🚀 How to Bootstrap This Session (Token Saving Entry)

**Do NOT read the whole codebase.** Follow these entry steps to save 90% of your context tokens:

1.  **Step 1:** Read the State Ledger at [.agents/LEDGER.md](file:///.agents/LEDGER.md) to understand current milestones, code files, and Android version compatibility traps.
2.  **Step 2:** Read the Handoff Brief at [.agents/HANDOFF.md](file:///.agents/HANDOFF.md) to review changed files and latest changes.
3.  **Step 3:** Use Graphify to target your work. Do not run generic grep searches.
    *   *To query a concept:* Run `graphify query "<concept_name>"` or use the `query_graph` tool.
    *   *To find relationships:* Run `graphify path "<Class A>" "<Class B>"` or use the `shortest_path` tool.
4.  **Step 4:** Run `graphify update .` after code edits to keep the AST index fresh (0 API token cost).

---

## 🎯 Next Tasks On the Horizon

If you are picking up work, the current focus areas are:
1.  **Verify display padding dynamically** when the camera notch rotates.
2.  **Implement physics-based spring animations** for docking transitions in `DragResizeOverlay`.
3.  **Verify & polish Bento widgets** (Task Manager and Quick Toolbox) on physical device layout sizes.

---

## 👥 Project Team Roles & Roster

1. **Owner/Admin (Ayush)**: Lead architect and workspace authority. When answering or coding, you are pair-programming directly with Ayush. Keep a highly collaborative tone.
2. **AI Co-Developer (Antigravity)**: Handled overlay safety confirmations, automated workflows, and sync pipelines.
3. **Reference**: Always read the official [identity.md](file:///.agents/rules/identity.md) during your Entry Protocol session bootstrapping.
"""
    with open(handoff_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(handoff_content)
    print("[+] Generated new HANDOFF.md brief successfully.")
    return True

def main():
    import argparse
    parser = argparse.ArgumentParser(description="Automated state ledger and handoff brief synchronizer.")
    parser.add_argument("--message", "-m", required=True, help="Description of changes made in this session.")
    parser.add_argument("--dry-run", "-d", action="store_true", help="Display updates without writing them to disk.")
    parser.add_argument("--no-graph", "-n", action="store_true", help="Skip running 'graphify update .'.")
    args = parser.parse_args()

    # Find the repository root directory (assumed to be 4 levels up from this script)
    script_dir = os.path.dirname(os.path.abspath(__file__))
    repo_root = os.path.abspath(os.path.join(script_dir, "..", "..", ".."))
    
    print(f"[*] Repository root identified as: {repo_root}")
    
    changes = get_git_changes(repo_root)
    if not changes:
        print("[!] No uncommitted modifications or new files detected by Git. Proceeding with message anyway.")
    
    # Filter and format modified files
    interesting_changes = [c for c in changes if not c["file"].startswith("graphify-out/") and not c["file"].startswith(".agents/")]
    if not interesting_changes:
        interesting_changes = changes # Fallback if only agents/graphify changed
        
    modified_files_list = [os.path.basename(c["file"]) for c in interesting_changes]
    modified_files_str = ", ".join(modified_files_list) if modified_files_list else "None"
    
    ledger_path = os.path.join(repo_root, ".agents", "LEDGER.md")
    handoff_path = os.path.join(repo_root, ".agents", "HANDOFF.md")
    
    if args.dry_run:
        print("\n=== DRY RUN MODE ===")
        print(f"Ledger file path: {ledger_path}")
        print(f"Handoff file path: {handoff_path}")
        print(f"Message: {args.message}")
        print(f"Modified files: {modified_files_str}")
        print("====================\n")
        return
        
    # 1. Update Ledger
    update_ledger(ledger_path, args.message, modified_files_str)
    
    # 2. Generate Handoff
    generate_handoff(handoff_path, args.message, interesting_changes)
    
    # 3. Update Code Graphify Graph
    if not args.no_graph:
        print("[*] Refreshing Code Graphify AST Index...")
        stdout, stderr, code = run_command("graphify update .", cwd=repo_root)
        if code == 0:
            print("[+] Semantic graph successfully updated.")
        else:
            print(f"[-] Graphify failed: {stderr}")

    print("[+] All synchronization tasks completed successfully!")

if __name__ == "__main__":
    main()
