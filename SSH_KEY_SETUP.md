# SSH Key Setup Guide

## ⚠️ SECURITY INCIDENT - ACTION REQUIRED

**Your SSH private key was accidentally committed to Git and is now compromised.**

### What Happened
- Private SSH key (`sshkey`) and public key (`sshkey.pub`) were committed in commit `bafe7dc`
- Even though they've been removed, they exist in Git history
- Anyone with access to the repository history can retrieve the private key
- **The old key is now compromised and must be revoked**

### Immediate Actions (Complete These Now)

1. ✅ **Remove from Git** (DONE)
   - Keys removed from repository
   - Added to .gitignore
   - Committed the removal

2. 🔴 **Revoke Old Key from All Services** (DO THIS NOW)
   - GitHub/GitLab: Remove the old public key
   - Any servers/VMs where this key was deployed
   - Any CI/CD systems using this key

3. 🔴 **Generate New SSH Keys** (Instructions below)

---

## How to Generate New SSH Keys

### Step 1: Generate a New Ed25519 Key (Recommended)

Ed25519 is the most secure and modern SSH key type.

```bash
# Generate new key with a descriptive comment
ssh-keygen -t ed25519 -C "your_email@example.com" -f ~/.ssh/id_ed25519_github

# When prompted:
# 1. Enter file location: Press Enter to use default OR specify custom path
# 2. Enter passphrase: USE A STRONG PASSPHRASE (highly recommended for security)
# 3. Re-enter passphrase: Confirm it
```

**What each flag means:**
- `-t ed25519`: Key type (Ed25519 is fastest and most secure)
- `-C "your_email@example.com"`: Comment to identify the key
- `-f ~/.ssh/id_ed25519_github`: Custom filename (optional, helps organize multiple keys)

### Step 2: Set Proper Permissions

SSH keys must have restrictive permissions or SSH will refuse to use them.

```bash
# Private key: Only you can read/write (600)
chmod 600 ~/.ssh/id_ed25519_github

# Public key: You can read/write, others can read (644)
chmod 644 ~/.ssh/id_ed25519_github.pub

# SSH directory: Only you can access (700)
chmod 700 ~/.ssh
```

### Step 3: Start SSH Agent

The SSH agent stores your key passphrase so you don't have to type it every time.

```bash
# Start the SSH agent
eval "$(ssh-agent -s)"

# Add your key to the agent (you'll be prompted for the passphrase)
ssh-add ~/.ssh/id_ed25519_github

# Verify it was added
ssh-add -l
```

### Step 4: Add Key to SSH Config (Optional but Recommended)

This makes it easier to use different keys for different services.

```bash
# Create or edit SSH config
nano ~/.ssh/config
```

Add this configuration:

```
# GitHub configuration
Host github.com
    HostName github.com
    User git
    IdentityFile ~/.ssh/id_ed25519_github
    AddKeysToAgent yes
    UseKeychain yes

# GitLab configuration (if you use it)
Host gitlab.com
    HostName gitlab.com
    User git
    IdentityFile ~/.ssh/id_ed25519_gitlab
    AddKeysToAgent yes
    UseKeychain yes

# Personal server example
Host myserver
    HostName 192.168.1.100
    User youruser
    IdentityFile ~/.ssh/id_ed25519_server
    Port 22
```

Set proper permissions:
```bash
chmod 600 ~/.ssh/config
```

### Step 5: Persist SSH Agent on macOS (Optional)

Add this to your `~/.zshrc` or `~/.bash_profile`:

```bash
# Auto-start SSH agent and add keys
if [ -z "$SSH_AUTH_SOCK" ]; then
   eval "$(ssh-agent -s)"
   ssh-add ~/.ssh/id_ed25519_github 2>/dev/null
fi
```

Then reload:
```bash
source ~/.zshrc
```

---

## Add SSH Key to GitHub

### Step 1: Copy Your Public Key

```bash
# Display your public key
cat ~/.ssh/id_ed25519_github.pub

# OR copy directly to clipboard (macOS)
pbcopy < ~/.ssh/id_ed25519_github.pub

# OR copy directly to clipboard (Linux with xclip)
xclip -selection clipboard < ~/.ssh/id_ed25519_github.pub
```

### Step 2: Add to GitHub

1. Go to GitHub.com and log in
2. Click your profile picture → **Settings**
3. In the left sidebar, click **SSH and GPG keys**
4. Click **New SSH key** (green button)
5. **Title**: Give it a descriptive name (e.g., "MacBook Air - October 2025")
6. **Key type**: Authentication Key
7. **Key**: Paste your public key (starts with `ssh-ed25519 AAAA...`)
8. Click **Add SSH key**
9. Confirm with your password if prompted

### Step 3: Test the Connection

```bash
# Test SSH connection to GitHub
ssh -T git@github.com

# Expected output:
# Hi username! You've successfully authenticated, but GitHub does not provide shell access.
```

If you see the success message, you're all set! ✅

---

## Add SSH Key to GitLab (If Needed)

Similar process:
1. GitLab.com → User Settings → SSH Keys
2. Paste public key
3. Give it a title and expiration date
4. Click **Add key**

Test:
```bash
ssh -T git@gitlab.com
```

---

## Add SSH Key to a Server

### Step 1: Copy Public Key to Server

```bash
# Method 1: Using ssh-copy-id (easiest)
ssh-copy-id -i ~/.ssh/id_ed25519_github.pub user@server-ip

# Method 2: Manual copy
cat ~/.ssh/id_ed25519_github.pub | ssh user@server-ip "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys"

# Method 3: Copy manually
# 1. Display key: cat ~/.ssh/id_ed25519_github.pub
# 2. SSH into server: ssh user@server-ip
# 3. Edit: nano ~/.ssh/authorized_keys
# 4. Paste key on new line
# 5. Save and exit
```

### Step 2: Set Proper Permissions on Server

```bash
# SSH into your server
ssh user@server-ip

# Set permissions
chmod 700 ~/.ssh
chmod 600 ~/.ssh/authorized_keys
```

### Step 3: Test Connection

```bash
# Try connecting (should work without password)
ssh user@server-ip
```

---

## Alternative: RSA Keys (If Ed25519 Not Supported)

Some older systems don't support Ed25519. Use RSA with 4096 bits:

```bash
# Generate RSA key
ssh-keygen -t rsa -b 4096 -C "your_email@example.com" -f ~/.ssh/id_rsa_github

# Same process as above for permissions, agent, config
```

---

## Best Practices for SSH Keys

### ✅ DO:
1. **Use strong passphrases** - Protects key if file is stolen
2. **Use different keys for different purposes** - GitHub, servers, work, personal
3. **Set proper permissions** - 600 for private keys, 644 for public keys
4. **Use Ed25519** - Fastest and most secure modern algorithm
5. **Add keys to SSH agent** - Convenience without sacrificing security
6. **Keep private keys on your machine only** - Never share or commit them
7. **Use SSH config file** - Makes managing multiple keys easier
8. **Regular rotation** - Consider rotating keys every 1-2 years

### ❌ DON'T:
1. **Don't commit private keys to Git** - This is what happened and why we're here
2. **Don't share private keys** - They're called "private" for a reason
3. **Don't use weak passphrases** - "password123" defeats the purpose
4. **Don't use 1024-bit RSA keys** - Too weak, use 4096-bit or Ed25519
5. **Don't store keys on shared systems** - Keep them on your personal machine
6. **Don't skip passphrases** - Even though it's optional, it's critical for security
7. **Don't reuse keys across different services** - Limits blast radius if compromised

---

## Verify Your Setup

### Check Your SSH Keys

```bash
# List all your SSH keys
ls -la ~/.ssh

# View your public key
cat ~/.ssh/id_ed25519_github.pub

# Check which keys are loaded in SSH agent
ssh-add -l

# View SSH agent status
echo $SSH_AUTH_SOCK
```

### Test Connections

```bash
# Test GitHub
ssh -T git@github.com

# Test with verbose output (debugging)
ssh -vT git@github.com

# Test a specific server
ssh user@server-ip

# Test with specific key
ssh -i ~/.ssh/id_ed25519_github user@server-ip
```

---

## Revoke the Old Compromised Key

### On GitHub:
1. Go to Settings → SSH and GPG keys
2. Find the old key (probably titled something generic or with old date)
3. Public key starts with: `ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIBbrZX2+9FcCLzpBnZy9WJfZ6maCDI4OlNG4xetAWk9R`
4. Click **Delete** next to it
5. Confirm deletion

### On Any Servers:
```bash
# SSH into each server where the old key was used
ssh user@server-ip

# Edit authorized_keys
nano ~/.ssh/authorized_keys

# Remove the line containing the old public key
# Save and exit (Ctrl+X, Y, Enter)
```

### On GitLab, Bitbucket, etc.:
Similar process - go to SSH key settings and delete the old key.

---

## Update Git Remote (If Using SSH)

If your repository is using HTTPS, you might want to switch to SSH now:

```bash
# Check current remote
git remote -v

# If it shows https://github.com/..., switch to SSH:
git remote set-url origin git@github.com:malcolmxsc/observability-sandbox.git

# Verify
git remote -v

# Test by fetching
git fetch
```

---

## Troubleshooting

### "Permission denied (publickey)"

**Cause**: SSH can't find or use your key

**Solutions**:
```bash
# 1. Check if key is loaded in agent
ssh-add -l

# 2. If not loaded, add it
ssh-add ~/.ssh/id_ed25519_github

# 3. Test with verbose output to see what's happening
ssh -vT git@github.com

# 4. Check SSH config
cat ~/.ssh/config

# 5. Verify key permissions
ls -la ~/.ssh/id_ed25519_github
# Should show: -rw-------  (600)
```

### "Bad permissions" error

**Cause**: SSH keys have incorrect permissions

**Fix**:
```bash
chmod 600 ~/.ssh/id_ed25519_github
chmod 644 ~/.ssh/id_ed25519_github.pub
chmod 700 ~/.ssh
chmod 600 ~/.ssh/config
```

### Agent not starting automatically

**Fix**: Add to `~/.zshrc`:
```bash
# Add at the end of the file
if [ -z "$SSH_AUTH_SOCK" ]; then
   eval "$(ssh-agent -s)"
   ssh-add ~/.ssh/id_ed25519_github 2>/dev/null
fi
```

Then: `source ~/.zshrc`

### Multiple keys but wrong one being used

**Fix**: Use SSH config to specify which key for which host:
```bash
# Edit config
nano ~/.ssh/config

# Add specific host configurations as shown above
```

---

## Summary Checklist

Complete this checklist to ensure proper SSH key setup:

- [ ] Generated new Ed25519 SSH key with passphrase
- [ ] Set proper permissions (600 private, 644 public)
- [ ] Added key to SSH agent
- [ ] Created/updated SSH config file
- [ ] Added public key to GitHub
- [ ] Tested GitHub SSH connection (`ssh -T git@github.com`)
- [ ] Added public key to any servers (if applicable)
- [ ] Added public key to GitLab/Bitbucket (if applicable)
- [ ] **REVOKED old compromised key from ALL services**
- [ ] Deleted old key files from disk
- [ ] Verified old key is NOT in `~/.ssh/`
- [ ] Updated Git remote to use SSH (optional)
- [ ] Added SSH agent auto-start to shell config (optional)
- [ ] Tested all connections work
- [ ] Documented which keys are for which purposes

---

## Quick Reference Commands

```bash
# Generate new key
ssh-keygen -t ed25519 -C "your_email@example.com" -f ~/.ssh/id_ed25519_github

# Set permissions
chmod 600 ~/.ssh/id_ed25519_github && chmod 644 ~/.ssh/id_ed25519_github.pub

# Add to agent
eval "$(ssh-agent -s)" && ssh-add ~/.ssh/id_ed25519_github

# Copy public key to clipboard (macOS)
pbcopy < ~/.ssh/id_ed25519_github.pub

# Test GitHub connection
ssh -T git@github.com

# View loaded keys
ssh-add -l

# Remove all keys from agent
ssh-add -D

# Add key to server
ssh-copy-id -i ~/.ssh/id_ed25519_github.pub user@server-ip
```

---

## Additional Resources

- [GitHub SSH Documentation](https://docs.github.com/en/authentication/connecting-to-github-with-ssh)
- [GitLab SSH Documentation](https://docs.gitlab.com/ee/user/ssh.html)
- [SSH Key Best Practices](https://www.ssh.com/academy/ssh/keygen)
- [Ed25519 vs RSA](https://security.stackexchange.com/questions/90077/ssh-key-ed25519-vs-rsa)

---

**Status**: ✅ Old keys removed from Git  
**Next**: Follow steps above to generate new keys and update all services  
**Priority**: CRITICAL - Do this immediately to secure your accounts
