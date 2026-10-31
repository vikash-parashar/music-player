/** Runs in <head> before paint: resolves Auto/Day/Night into html[data-theme] so there is no flash. */
export const themeScript = `(function(){try{var m=localStorage.getItem('ampli-mode')||'auto';var d=m==='dark'||(m==='auto'&&matchMedia('(prefers-color-scheme: dark)').matches);document.documentElement.dataset.theme=d?'dark':'light'}catch(e){document.documentElement.dataset.theme='light'}})()`;
