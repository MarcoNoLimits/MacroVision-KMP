param(
    [string]$Sql,
    [string]$SqlFile,
    [string]$ProjectRef = "mpsqdaptkkasjoepamwl"
)

$ProgressPreference = 'SilentlyContinue'

if (-not ("SupabaseCredReader" -as [type])) {
Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
using System.Text;

public class SupabaseCredReader {
    [DllImport("advapi32.dll", EntryPoint = "CredReadW", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern bool CredRead(string target, int type, int reservedFlag, out IntPtr credentialPtr);

    [DllImport("advapi32.dll", EntryPoint = "CredFree", SetLastError = true)]
    public static extern void CredFree(IntPtr buffer);

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct CREDENTIAL {
        public int Flags;
        public int Type;
        public string TargetName;
        public string Comment;
        public System.Runtime.InteropServices.ComTypes.FILETIME LastWritten;
        public int CredentialBlobSize;
        public IntPtr CredentialBlob;
        public int Persist;
        public int AttributeCount;
        public IntPtr Attributes;
        public string TargetAlias;
        public string UserName;
    }

    public static string GetToken() {
        IntPtr ptr;
        if (CredRead("Supabase CLI:supabase", 1, 0, out ptr)) {
            try {
                CREDENTIAL cred = (CREDENTIAL)Marshal.PtrToStructure(ptr, typeof(CREDENTIAL));
                if (cred.CredentialBlobSize > 0) {
                    byte[] bytes = new byte[cred.CredentialBlobSize];
                    Marshal.Copy(cred.CredentialBlob, bytes, 0, cred.CredentialBlobSize);
                    return Encoding.UTF8.GetString(bytes);
                }
            } finally {
                CredFree(ptr);
            }
        }
        return Environment.GetEnvironmentVariable("SUPABASE_ACCESS_TOKEN");
    }
}
"@
}

$token = [SupabaseCredReader]::GetToken()
if (-not $token) {
    Write-Error "Could not retrieve Supabase access token."
    exit 1
}

$queryText = $Sql
if ($SqlFile) {
    if (Test-Path $SqlFile) {
        $queryText = Get-Content -Raw -Path $SqlFile -Encoding UTF8
    } else {
        Write-Error "File not found: $SqlFile"
        exit 1
    }
}

if (-not $queryText) {
    Write-Error "No SQL query provided."
    exit 1
}

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type"  = "application/json"
}

$payload = @{ query = $queryText } | ConvertTo-Json -Depth 10

try {
    $response = Invoke-RestMethod -Uri "https://api.supabase.com/v1/projects/$ProjectRef/database/query" `
        -Method Post `
        -Headers $headers `
        -Body $payload
    $response | ConvertTo-Json -Depth 10
} catch {
    if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
        Write-Error "API Error: $($_.ErrorDetails.Message)"
    } elseif ($_.Exception -and $_.Exception.Response) {
        try {
            $stream = $_.Exception.Response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            $errBody = $reader.ReadToEnd()
            Write-Error "HTTP Error: $errBody"
        } catch {
            Write-Error "Exception: $($_.Exception.Message)"
        }
    } else {
        Write-Error "Error: $($_.Exception.Message)"
    }
    exit 1
}
