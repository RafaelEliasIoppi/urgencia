# SGPUR Desktop — gerar, instalar e fazer backup

O SGPUR pode rodar como **aplicativo desktop standalone** numa única máquina,
**sem servidor, sem internet e sem banco externo**. Os dados ficam num banco
local (H2) em `~/.sgpur`.

## Gerar o executável (quem desenvolve)
Pré-requisito: **JDK 21** (já configurado em `package-desktop.ps1`).

```powershell
.\package-desktop.ps1
```
Isso builda o JAR e roda o `jpackage`, produzindo:
- `dist\desktop\SGPUR\SGPUR.exe` — executável com **Java embutido** (o usuário
  final **não precisa ter Java instalado**).
- `dist\SGPUR-desktop.zip` — o mesmo, compactado para distribuir (~106 MB).

## Instalar/usar (usuário final)
1. Copie a pasta `SGPUR` (ou descompacte o `SGPUR-desktop.zip`) para onde quiser
   (ex.: `C:\SGPUR`).
2. Dê duplo clique em **`SGPUR.exe`**.
3. O app inicia e **abre o navegador** automaticamente em `http://localhost:8080`.
4. Login inicial: **admin / admin123** (troque a senha em Usuários).

> Dica: crie um atalho do `SGPUR.exe` na Área de Trabalho / Menu Iniciar.

## Onde ficam os dados
- Banco: `C:\Users\<voce>\.sgpur\db\sgpur.mv.db`
- Anexos: `C:\Users\<voce>\.sgpur\anexos\`

## Backup e restauração
- **Backup:** copie a pasta inteira `C:\Users\<voce>\.sgpur` para um pendrive/
  nuvem. (Faça com o app fechado, para o banco não estar em uso.)
- **Restaurar:** substitua a pasta `.sgpur` pela do backup.

## Observações
- Por ser standalone, **os dados ficam nessa máquina** — não são compartilhados
  com outros computadores. Se precisar compartilhar entre vários PCs no futuro,
  dá para apontar para um banco central (perfil `prod`/PostgreSQL); o código já
  suporta.
- Para fechar o app: feche a janela do `SGPUR.exe` (ou encerre pelo Gerenciador
  de Tarefas). O navegador é só a interface.
