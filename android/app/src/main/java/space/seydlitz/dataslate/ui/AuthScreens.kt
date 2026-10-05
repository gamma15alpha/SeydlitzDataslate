package space.seydlitz.dataslate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import space.seydlitz.dataslate.auth.FormStatus

@Composable
fun LoginScreen(status: FormStatus, notice: String?, onLogin: (String, String) -> Unit, onRegister: () -> Unit) {
    var login by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    AuthForm("Вход", status, "Войти", { onLogin(login, password) }, "Регистрация по инвайту", onRegister) {
        notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Field("Логин", login, { login = it })
        Field("Пароль", password, { password = it }, password = true)
    }
}

@Composable
fun RegisterScreen(status: FormStatus, onRegister: (String, String, String, String, String) -> Unit, onBack: () -> Unit) {
    var invite by rememberSaveable { mutableStateOf("") }
    var login by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }

    AuthForm(
        "Регистрация", status, "Зарегистрироваться", { onRegister(invite, login, displayName, password, repeat) },
        "Уже есть аккаунт — войти", onBack,
    ) {
        Field("Инвайт", invite, { invite = it })
        Field("Логин", login, { login = it })
        Field("Имя (необязательно)", displayName, { displayName = it })
        Field("Пароль", password, { password = it }, password = true)
        Field("Пароль ещё раз", repeat, { repeat = it }, password = true)
    }
}

@Composable
private fun AuthForm(
    title: String,
    status: FormStatus,
    submitLabel: String,
    onSubmit: () -> Unit,
    switchLabel: String,
    onSwitch: () -> Unit,
    fields: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title.uppercase(), style = MaterialTheme.typography.headlineMedium)
        Column(Modifier.widthIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            fields()
            status.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onSubmit, Modifier.fillMaxWidth(), enabled = !status.busy) {
                Text(if (status.busy) "…" else submitLabel.uppercase())
            }
            TextButton(onSwitch, Modifier.fillMaxWidth()) {
                Text(switchLabel, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, password: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
