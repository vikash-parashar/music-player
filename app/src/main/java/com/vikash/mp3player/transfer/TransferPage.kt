package com.vikash.mp3player.transfer

/** The upload page the computer's browser shows. Each file is sent with its own PUT, with progress. */
internal fun page(token: String): String = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Send music to Ampli</title>
<style>
  :root { color-scheme: light dark; --rose:#E11D48; --slate:#334155; }
  * { box-sizing: border-box; }
  body { margin:0; font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif; background:#0F0D14; color:#F5F3F7; }
  header { background: linear-gradient(90deg, var(--slate), var(--rose)); padding: 28px 20px; }
  header h1 { margin:0; font-size: 26px; }
  header p { margin:6px 0 0; opacity:.9; }
  main { max-width: 720px; margin: 0 auto; padding: 20px; }
  #drop { border: 2px dashed #6B6475; border-radius: 18px; padding: 48px 20px; text-align:center; cursor:pointer; transition: .15s; }
  #drop.over { border-color: var(--rose); background: rgba(225,29,72,.08); }
  #drop strong { font-size: 18px; display:block; margin-bottom: 6px; }
  button { background: var(--rose); color:#fff; border:0; border-radius: 999px; padding: 10px 20px; font-size: 15px; margin-top: 14px; cursor:pointer; }
  ul { list-style:none; padding:0; margin: 20px 0 0; }
  li { background:#1C1820; border-radius: 12px; padding: 12px 14px; margin-bottom: 8px; }
  .row { display:flex; justify-content: space-between; gap: 12px; font-size: 14px; }
  .name { overflow:hidden; text-overflow: ellipsis; white-space: nowrap; }
  .bar { height: 6px; border-radius: 3px; background:#2B2F3A; margin-top: 8px; overflow:hidden; }
  .bar > div { height:100%; width:0; background: linear-gradient(90deg, var(--slate), var(--rose)); }
  .ok { color:#4ADE80; } .err { color:#FDA4AF; }
  footer { opacity:.6; font-size: 13px; margin-top: 24px; }
</style>
</head>
<body>
<header><h1>Send music to Ampli</h1><p>Drop songs here. They go straight to your phone over Wi-Fi.</p></header>
<main>
  <div id="drop">
    <strong>Drag and drop music files here</strong>
    MP3, M4A, FLAC, OGG, OPUS, WAV
    <br><button id="pick" type="button">Choose files</button>
    <input id="file" type="file" multiple accept="audio/*,.mp3,.m4a,.flac,.ogg,.opus,.wav,.aac,.wma" hidden>
  </div>
  <ul id="list"></ul>
  <footer>Keep the Ampli transfer screen open on your phone until all files finish.</footer>
</main>
<script>
  const drop = document.getElementById('drop'), input = document.getElementById('file'), list = document.getElementById('list');
  const queue = []; let busy = false;
  document.getElementById('pick').onclick = (e) => { e.stopPropagation(); input.click(); };
  drop.onclick = () => input.click();
  input.onchange = () => { add(input.files); input.value = ''; };
  ['dragenter','dragover'].forEach(t => drop.addEventListener(t, e => { e.preventDefault(); drop.classList.add('over'); }));
  ['dragleave','drop'].forEach(t => drop.addEventListener(t, e => { e.preventDefault(); drop.classList.remove('over'); }));
  drop.addEventListener('drop', e => add(e.dataTransfer.files));

  function add(files) {
    for (const f of files) {
      const li = document.createElement('li');
      li.innerHTML = '<div class="row"><span class="name"></span><span class="st">Waiting</span></div><div class="bar"><div></div></div>';
      li.querySelector('.name').textContent = f.name;
      list.prepend(li);
      queue.push({ f, li });
    }
    next();
  }

  function next() {
    if (busy || !queue.length) return;
    busy = true;
    const { f, li } = queue.shift();
    const st = li.querySelector('.st'), bar = li.querySelector('.bar > div');
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', '/$token/upload?name=' + encodeURIComponent(f.name));
    xhr.upload.onprogress = e => { if (e.lengthComputable) { const p = Math.round(e.loaded * 100 / e.total); bar.style.width = p + '%'; st.textContent = p + '%'; } };
    xhr.onload = () => {
      let ok = xhr.status === 200, msg = 'Failed';
      try { const r = JSON.parse(xhr.responseText); if (r.error) msg = r.error; } catch (e) {}
      st.textContent = ok ? 'Sent ✓' : msg; st.className = 'st ' + (ok ? 'ok' : 'err');
      bar.style.width = '100%'; busy = false; next();
    };
    xhr.onerror = () => { st.textContent = 'Phone not reachable. Is the Ampli screen still open?'; st.className = 'st err'; busy = false; next(); };
    xhr.send(f);
  }
</script>
</body>
</html>
""".trimIndent()
