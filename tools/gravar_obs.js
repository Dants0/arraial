// Records a Roteiro film with OBS (obs-websocket v5, built into OBS 28+).
// - Connects to OBS on localhost:4455 with the password from OBS's own config (never printed).
// - Creates (once) a scene "Arraial - gravação automática" capturing the dev game window and its sound,
//   switches to it, and records between the "GRAVAR inicio" and "GRAVAR fim" lines of the game log.
// - Afterwards switches back to the scene that was live and prints where the file went.
// Usage: node tools/gravar_obs.js [path to the game log]   (start it, then start the Roteiro)
'use strict';
const fs = require('fs'), path = require('path'), crypto = require('crypto');

const LOG = process.argv[2] || path.join(__dirname, '..', 'run', 'logs', 'latest.log');
const CONFIG = path.join(process.env.APPDATA, 'obs-studio', 'plugin_config', 'obs-websocket', 'config.json');
const CENA = 'Arraial - gravação automática';
const JANELA = 'Minecraft* 26.3:SDL_app:java.exe';
const LIMITE_MS = 25 * 60 * 1000;

const cfg = JSON.parse(fs.readFileSync(CONFIG, 'utf8'));
if (!cfg.server_enabled) {
  console.error('O servidor WebSocket do OBS está desligado (Ferramentas → Configurações do servidor WebSocket → Ativar).');
  process.exit(2);
}

let ws, proximo = 1;
const pendentes = new Map();
function pedir(requestType, requestData = {}) {
  const requestId = String(proximo++);
  ws.send(JSON.stringify({ op: 6, d: { requestType, requestId, requestData } }));
  return new Promise((ok, erro) => pendentes.set(requestId, { ok, erro, requestType }));
}
const sha = s => crypto.createHash('sha256').update(s).digest('base64');

function conectar() {
  return new Promise((ok, erro) => {
    ws = new WebSocket(`ws://127.0.0.1:${cfg.server_port || 4455}`);
    ws.onerror = () => erro(new Error('não consegui conectar no OBS (ele está aberto?)'));
    ws.onmessage = ev => {
      const m = JSON.parse(ev.data);
      if (m.op === 0) {
        const id = { rpcVersion: 1 };
        if (m.d.authentication) {
          const segredo = sha(cfg.server_password + m.d.authentication.salt);
          id.authentication = sha(segredo + m.d.authentication.challenge);
        }
        ws.send(JSON.stringify({ op: 1, d: id }));
      } else if (m.op === 2) {
        ok();
      } else if (m.op === 7) {
        const p = pendentes.get(m.d.requestId);
        if (!p) return;
        pendentes.delete(m.d.requestId);
        if (m.d.requestStatus.result) p.ok(m.d.responseData || {});
        else p.erro(new Error(`${p.requestType}: ${m.d.requestStatus.comment || m.d.requestStatus.code}`));
      }
    };
    ws.onclose = ev => { if (ev.code === 4009) erro(new Error('senha do WebSocket recusada')); };
  });
}

async function prepararCena() {
  const { scenes } = await pedir('GetSceneList');
  if (!scenes.some(s => s.sceneName === CENA)) {
    await pedir('CreateScene', { sceneName: CENA });
    await pedir('CreateInput', { sceneName: CENA, inputName: 'Arraial - janela do jogo', inputKind: 'window_capture',
      inputSettings: { window: JANELA, priority: 2, method: 2, cursor: false, client_area: true } });
    await pedir('CreateInput', { sceneName: CENA, inputName: 'Arraial - som do jogo', inputKind: 'wasapi_process_output_capture',
      inputSettings: { window: JANELA, priority: 2 } });
    console.log(`cena "${CENA}" criada`);
  }
  // Stretch the capture to the whole canvas.
  const { sceneItemId } = await pedir('GetSceneItemId', { sceneName: CENA, sourceName: 'Arraial - janela do jogo' });
  const v = await pedir('GetVideoSettings');
  await pedir('SetSceneItemTransform', { sceneName: CENA, sceneItemId, sceneItemTransform: {
    boundsType: 'OBS_BOUNDS_SCALE_INNER', boundsWidth: v.baseWidth, boundsHeight: v.baseHeight, positionX: 0, positionY: 0, alignment: 5 } });
}

async function main() {
  await conectar();
  const { currentProgramSceneName: antes } = await pedir('GetCurrentProgramScene');
  await prepararCena();
  console.log(`conectado; cena atual "${antes}". Esperando "GRAVAR inicio" em ${LOG}`);
  let pos = fs.existsSync(LOG) ? fs.statSync(LOG).size : 0;
  let gravando = false, terminou = false;
  const inicio = Date.now();
  const restaurar = async () => {
    try { await pedir('SetCurrentProgramScene', { sceneName: antes }); } catch (e) { /* ignore */ }
  };
  while (!terminou) {
    await new Promise(r => setTimeout(r, 500));
    if (Date.now() - inicio > LIMITE_MS) {
      console.log('tempo esgotado');
      break;
    }
    if (!fs.existsSync(LOG)) continue;
    const tam = fs.statSync(LOG).size;
    if (tam < pos) pos = 0; // a new log (the game restarted)
    if (tam === pos) continue;
    const fd = fs.openSync(LOG, 'r');
    const buf = Buffer.alloc(tam - pos);
    fs.readSync(fd, buf, 0, buf.length, pos);
    fs.closeSync(fd);
    pos = tam;
    for (const linha of buf.toString('utf8').split('\n')) {
      if (!gravando && linha.includes('[ROTEIRO] GRAVAR inicio')) {
        await pedir('SetCurrentProgramScene', { sceneName: CENA });
        await new Promise(r => setTimeout(r, 400));
        await pedir('StartRecord');
        gravando = true;
        console.log('gravando...');
      } else if (gravando && linha.includes('[ROTEIRO] GRAVAR fim')) {
        const r = await pedir('StopRecord');
        console.log('gravação salva em: ' + (r.outputPath || '(veja a pasta de gravações do OBS)'));
        gravando = false;
        terminou = true;
      } else if (linha.includes('[ROTEIRO] fim do roteiro') && !gravando) {
        terminou = true;
      }
    }
  }
  if (gravando) {
    const r = await pedir('StopRecord');
    console.log('gravação interrompida, salva em: ' + (r.outputPath || '?'));
  }
  await restaurar();
  ws.close();
}

main().catch(e => {
  console.error('erro: ' + e.message);
  process.exit(1);
});
