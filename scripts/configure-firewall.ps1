[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Add', 'Remove')]
    [string]$Action,

    [Parameter(Mandatory = $true)]
    [string]$Executable,

    [ValidateRange(1, 65535)]
    [int]$Port = 8765
)

$ErrorActionPreference = 'Stop'
$resolvedExecutable = [System.IO.Path]::GetFullPath($Executable)
$rules = @(
    @{
        Name = 'Smart Remote RemoteCore (Private LocalSubnet)'
        Protocol = 'TCP'
        LocalPort = $Port
        Description = "WebSocket control on TCP $Port"
    },
    @{
        Name = 'Smart Remote Discovery (Private LocalSubnet)'
        Protocol = 'UDP'
        LocalPort = 5353
        Description = 'automatic PC discovery on UDP 5353'
    }
)

if ($Action -eq 'Add' -and -not (Test-Path -LiteralPath $resolvedExecutable -PathType Leaf)) {
    throw "RemoteCore executable not found: $resolvedExecutable"
}

if ($Action -eq 'Add') {
    foreach ($rule in $rules) {
        $existing = Get-NetFirewallRule -DisplayName $rule.Name -ErrorAction SilentlyContinue
        if ($null -ne $existing) {
            Write-Host "Firewall rule '$($rule.Name)' is already installed; it was not changed."
            continue
        }
        if ($PSCmdlet.ShouldProcess($rule.Name, "Add Private/LocalSubnet inbound rule")) {
            New-NetFirewallRule `
                -DisplayName $rule.Name `
                -Direction Inbound `
                -Action Allow `
                -Enabled True `
                -Profile Private `
                -Program $resolvedExecutable `
                -Protocol $rule.Protocol `
                -LocalPort $rule.LocalPort `
                -RemoteAddress LocalSubnet | Out-Null
            Write-Host "Added '$($rule.Name)' for $resolvedExecutable ($($rule.Description), Private, LocalSubnet)."
        }
    }
} else {
    foreach ($rule in $rules) {
        $existing = Get-NetFirewallRule -DisplayName $rule.Name -ErrorAction SilentlyContinue
        if ($null -eq $existing) {
            Write-Host "Firewall rule '$($rule.Name)' is not installed."
            continue
        }
        if ($PSCmdlet.ShouldProcess($rule.Name, 'Remove inbound rule')) {
            $existing | Remove-NetFirewallRule
            Write-Host "Removed '$($rule.Name)'."
        }
    }
}
