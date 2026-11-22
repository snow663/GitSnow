package com.gitsnow.app

import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { GitSnowApp() } }
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
private fun joinPath(vararg segments: String): String =
    segments.filter { it.isNotBlank() }.joinToString("/")

@Composable
private fun GitSnowApp() {
    val context = LocalContext.current
    val store = remember { TokenStore(context) }
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(store.load()) }
    var inputToken by remember { mutableStateOf("") }
    var repositories by remember { mutableStateOf(emptyList<JSONObject>()) }
    var repo by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("main") }
    var availableBranches by remember { mutableStateOf(emptyList<String>()) }
    var branchMenu by remember { mutableStateOf(false) }
    var path by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(emptyList<JSONObject>()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var newFileName by remember { mutableStateOf("") }
    var newRepoName by remember { mutableStateOf("") }
    var privateRepo by remember { mutableStateOf(true) }
    var editingPath by remember { mutableStateOf("") }
    var editingText by remember { mutableStateOf("") }

    fun task(success: String = "Done", block: suspend () -> Unit) {
        scope.launch {
            busy = true
            status = ""
            try {
                withContext(Dispatchers.IO) { block() }
                status = success
            } catch (e: Exception) { status = e.message ?: "Operation failed" }
            finally { busy = false }
        }
    }
    fun refreshRepos() {
        if (token.isNotBlank()) task("Repositories loaded") {
            val data = Github(token).repositories().objects()
            withContext(Dispatchers.Main) { repositories = data }
        }
    }
    fun refreshDirectory(r: String = repo, p: String = path, b: String = branch) {
        if (r.isNotBlank()) task("Directory refreshed") {
            val data = Github(token).contents(r, p, b).objects()
            withContext(Dispatchers.Main) { entries = data }
        }
    }
    fun chooseRepo(name: String, defaultBranch: String) {
        repo = name; branch = defaultBranch; path = ""; entries = emptyList()
        refreshDirectory(name, "", defaultBranch)
        scope.launch {
            try {
                val names = withContext(Dispatchers.IO) {
                    Github(token).branches(name).objects().map { it.getString("name") }
                }
                availableBranches = names
            } catch (e: Exception) { status = e.message ?: "Could not load branches" }
        }
    }

    val uploadPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty() && repo.isNotBlank()) {
            val targetRepo = repo; val targetBranch = branch; val targetPath = path
            task("Uploaded ${uris.size} file(s)") {
                val api = Github(token)
                for (uri in uris) {
                    val fileName = context.contentResolver.query(uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: error("Could not determine file name")
                    val data = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Unable to read $fileName")
                    api.put(targetRepo, joinPath(targetPath, fileName),
                        targetBranch, data, "Upload $fileName")
                }
                val updated = api.contents(targetRepo, targetPath, targetBranch).objects()
                withContext(Dispatchers.Main) { entries = updated }
            }
        }
    }

    LaunchedEffect(token) {
        if (token.isNotBlank()) refreshRepos()
    }
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Text("❄️ GitSnow", style = MaterialTheme.typography.headlineMedium)
        if (token.isBlank()) {
            Text("Enter a GitHub personal access token. Grant Contents: Read/Write and Metadata: Read for your repositories.")
            OutlinedTextField(inputToken, { inputToken = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text("GitHub access token") })
            Button(enabled = inputToken.isNotBlank(), onClick = {
                store.save(inputToken.trim())
                token = inputToken.trim()
                inputToken = ""
            }) { Text("Connect") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { repo = ""; path = ""; editingPath = ""; refreshRepos() },
                    enabled = !busy) { Text("Repositories") }
                TextButton(onClick = {
                    store.clear(); token = ""; repo = ""; repositories = emptyList()
                }) { Text("Sign out") }
            }
            if (repo.isBlank()) {
                OutlinedTextField(newRepoName, { newRepoName = it },
                    label = { Text("New repository") }, singleLine = true)
                Row {
                    Checkbox(privateRepo, { privateRepo = it })
                    Text("Private", Modifier.padding(top = 12.dp))
                }
                Button(enabled = !busy && newRepoName.isNotBlank(), onClick = {
                    val name = newRepoName.trim()
                    task("Repository created") {
                        Github(token).createRepository(name, privateRepo)
                        val data = Github(token).repositories().objects()
                        withContext(Dispatchers.Main) {
                            repositories = data
                            newRepoName = ""
                        }
                    }
                }) { Text("Create repository") }
                LazyColumn(Modifier.weight(1f)) {
                    items(repositories, key = { it.optString("id") }) { item ->
                        ListItem(
                            headlineContent = { Text(item.optString("full_name")) },
                            supportingContent = {
                                Text(if (item.optBoolean("private")) "Private" else "Public")
                            },
                            modifier = Modifier.clickable(enabled = !busy) {
                                chooseRepo(item.getString("full_name"),
                                    item.optString("default_branch", "main"))
                            }
                        )
                        HorizontalDivider()
                    }
                }
            } else if (editingPath.isNotBlank()) {
                Text(editingPath, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(editingText, { editingText = it },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    label = { Text("UTF-8 text") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy, onClick = {
                        val target = editingPath
                        task("Changes committed") {
                            val api = Github(token)
                            api.put(repo, target, branch, editingText.toByteArray(Charsets.UTF_8),
                                "Edit $target")
                            val data = api.contents(repo, path, branch).objects()
                            withContext(Dispatchers.Main) {
                                editingPath = ""
                                entries = data
                            }
                        }
                    }) { Text("Commit") }
                    TextButton(onClick = { editingPath = "" }) { Text("Cancel") }
                }
            } else {
                Text(repo, style = MaterialTheme.typography.titleMedium)
                Box {
                    TextButton(onClick = { branchMenu = true }) { Text("Branch: $branch ▾") }
                    DropdownMenu(expanded = branchMenu,
                        onDismissRequest = { branchMenu = false }) {
                        availableBranches.forEach { value ->
                            DropdownMenuItem(text = { Text(value) }, onClick = {
                                branchMenu = false
                                branch = value
                                refreshDirectory(repo, path, value)
                            })
                        }
                    }
                }
                Text("/$path")
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Button(enabled = path.isNotBlank() && !busy, onClick = {
                        path = path.substringBeforeLast('/', "")
                        refreshDirectory()
                    }) { Text("Up") }
                    Button(enabled = !busy,
                        onClick = { uploadPicker.launch(arrayOf("*/*")) }) {
                        Text("Upload")
                    }
                    TextButton(enabled = !busy, onClick = { refreshDirectory() }) {
                        Text("Refresh")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(folderName, { folderName = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Folder name") }, singleLine = true)
                    Button(enabled = !busy && folderName.isNotBlank() &&
                        folderName.trim() !in listOf(".", "..") &&
                        !folderName.contains('/') && !folderName.contains('\\'),
                        onClick = {
                            val dir = folderName.trim()
                            task("Folder created") {
                                val api = Github(token)
                                api.put(repo, joinPath(path, dir, ".gitkeep"),
                                    branch, byteArrayOf(), "Create folder $dir")
                                val data = api.contents(repo, path, branch).objects()
                                withContext(Dispatchers.Main) {
                                    folderName = ""; entries = data
                                }
                            }
                        }) { Text("Add") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(newFileName, { newFileName = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("New text file") }, singleLine = true)
                    Button(enabled = !busy && newFileName.isNotBlank() &&
                        !newFileName.contains('/') && newFileName !in listOf(".", ".."),
                        onClick = {
                            editingPath = joinPath(path, newFileName.trim())
                            editingText = ""
                            newFileName = ""
                        }) { Text("Create") }
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(entries, key = { it.optString("path") }) { item ->
                        val name = item.optString("name")
                        val itemPath = item.optString("path")
                        val isDir = item.optString("type") == "dir"
                        var confirmDelete by remember(itemPath) { mutableStateOf(false) }
                        ListItem(
                            headlineContent = { Text((if (isDir) "📁 " else "📄 ") + name) },
                            supportingContent = {
                                if (!isDir) {
                                    Row {
                                        TextButton(enabled = !busy, onClick = {
                                            task("File loaded") {
                                                val metadata = Github(token).file(repo, itemPath, branch)
                                                require(metadata.optString("encoding") == "base64") {
                                                    "This file cannot be edited as text"
                                                }
                                                val bytes = Base64.decode(
                                                    metadata.getString("content").replace("\n", ""),
                                                    Base64.DEFAULT)
                                                require(bytes.none { it == 0.toByte() }) {
                                                    "Binary file: text editing disabled"
                                                }
                                                val text = bytes.toString(Charsets.UTF_8)
                                                withContext(Dispatchers.Main) {
                                                    editingText = text; editingPath = itemPath
                                                }
                                            }
                                        }) { Text("Edit") }
                                        TextButton(enabled = !busy,
                                            onClick = { confirmDelete = !confirmDelete }) {
                                            Text(if (confirmDelete) "Cancel" else "Delete")
                                        }
                                        if (confirmDelete) TextButton(enabled = !busy, onClick = {
                                            task("File deleted") {
                                                val api = Github(token)
                                                api.delete(repo, itemPath, branch)
                                                val data = api.contents(repo, path, branch).objects()
                                                withContext(Dispatchers.Main) { entries = data }
                                            }
                                        }) { Text("Confirm") }
                                    }
                                }
                            },
                            modifier = Modifier.clickable(enabled = isDir && !busy) {
                                path = itemPath
                                refreshDirectory()
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
    }
}
