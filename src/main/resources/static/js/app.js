const API = "/api/livros";
const API_AUTORES = "/api/autores";
const LIMITE_BUSCA = 12;
const STATUS_LEITURA = [
    { codigo: "quero_ler", rotulo: "Quero ler" },
    { codigo: "lendo", rotulo: "Lendo" },
    { codigo: "lido", rotulo: "Lido" }
];

const form = document.getElementById("form-livro");
const lista = document.getElementById("lista-livros");
const aviso = document.getElementById("aviso");
const botaoSalvar = document.getElementById("botao-salvar");
const botaoBuscar = document.getElementById("botao-buscar");
const botaoCatalogo = document.getElementById("botao-catalogo");
const botaoMais = document.getElementById("botao-mais");
const buscaInput = document.getElementById("busca-titulo");
const termoCatalogo = document.getElementById("termo-catalogo");
const painelCatalogo = document.getElementById("painel-catalogo");
const resultados = document.getElementById("resultados-busca");
const maisResultados = document.getElementById("mais-resultados");
const tituloSelecionado = document.getElementById("titulo-selecionado");
const capaImagem = document.getElementById("capa-imagem");
const capaLegenda = document.getElementById("capa-legenda");
const opcoesCapa = document.getElementById("opcoes-capa");
const gradeCapas = document.getElementById("grade-capas");
const totalLivros = document.getElementById("total-livros");
const ficha = document.getElementById("ficha-detalhe");
const detalheCapa = document.getElementById("detalhe-capa");
const detalheSemCapa = document.getElementById("detalhe-sem-capa");
const detalheDescricao = document.getElementById("detalhe-descricao");
const detalheStatus = document.getElementById("detalhe-status");
const dialog = document.getElementById("confirma-exclusao");
const confirmaTexto = document.getElementById("confirma-texto");
const buscaAutor = document.getElementById("busca-autor");
const botaoBuscarAutor = document.getElementById("botao-buscar-autor");
const listaAutores = document.getElementById("lista-autores");
const totalAutores = document.getElementById("total-autores");
const biografiaAutor = document.getElementById("biografia-autor");
const resumoMetas = document.getElementById("resumo-metas");

let livrosCache = [];
let acervoCompleto = [];
let resultadosCache = [];
let autoresCache = [];
let selecionado = null;
let capaEscolhida = null;
let fichaAberta = null;
let exclusaoPendente = null;
let avisoTimer = null;
let buscaCatalogoTimer = null;
let filtrandoAcervo = false;
let modoAcervo = localStorage.getItem("acervo.modo") === "lista" ? "lista" : "grade";
let termoAcervoAtual = "";
let paginaAtual = 0;
let temMaisResultados = false;
let consultaAtual = "";
let buscandoMais = false;
let buscaDebounce = null;
let buscaAbort = null;
let buscaSeq = 0;
let autorSelecionado = null;
let wikiAbort = null;
let abaAtual = localStorage.getItem("acervo.aba") || "acervo";

document.addEventListener("DOMContentLoaded", () => {
    form.addEventListener("submit", salvarLivro);
    botaoBuscar.addEventListener("click", () => {
        clearTimeout(buscaDebounce);
        buscarObras(false);
    });
    botaoMais.addEventListener("click", () => buscarObras(true));
    buscaInput.addEventListener("input", () => {
        clearTimeout(buscaDebounce);
        const termo = buscaInput.value.trim();
        if (termo.length < 2) {
            if (buscaAbort) {
                buscaAbort.abort();
            }
            resultadosCache = [];
            paginaAtual = 0;
            temMaisResultados = false;
            resultados.hidden = true;
            resultados.innerHTML = "";
            maisResultados.hidden = true;
            return;
        }
        buscaDebounce = setTimeout(() => buscarObras(false), 300);
    });
    buscaInput.addEventListener("keydown", (evento) => {
        if (evento.key === "Enter") {
            evento.preventDefault();
            clearTimeout(buscaDebounce);
            buscarObras(false);
        }
    });
    resultados.addEventListener("click", aoEscolherResultado);
    gradeCapas.addEventListener("click", aoEscolherCapa);
    lista.addEventListener("click", aoClicarNoAcervo);
    botaoCatalogo.addEventListener("click", alternarBuscaCatalogo);
    document.getElementById("modo-grade").addEventListener("click", () => definirModoAcervo("grade"));
    document.getElementById("modo-lista").addEventListener("click", () => definirModoAcervo("lista"));
    atualizarBotoesModo();
    aplicarClasseModo();
    termoCatalogo.addEventListener("input", () => {
        clearTimeout(buscaCatalogoTimer);
        buscaCatalogoTimer = setTimeout(() => carregarLivros(termoCatalogo.value.trim()), 250);
    });
    document.getElementById("fechar-detalhe").addEventListener("click", () => ficha.close());
    document.getElementById("salvar-sinopse").addEventListener("click", salvarSinopse);
    document.getElementById("excluir-detalhe").addEventListener("click", pedirExclusao);
    document.getElementById("cancelar-exclusao").addEventListener("click", () => dialog.close());
    document.getElementById("confirmar-exclusao").addEventListener("click", confirmarExclusao);
    detalheStatus.addEventListener("click", aoClicarStatusDetalhe);
    document.querySelector(".abas").addEventListener("click", aoTrocarAba);
    botaoBuscarAutor.addEventListener("click", () => buscarBiografia(buscaAutor.value.trim()));
    buscaAutor.addEventListener("keydown", (evento) => {
        if (evento.key === "Enter") {
            evento.preventDefault();
            buscarBiografia(buscaAutor.value.trim());
        }
    });
    listaAutores.addEventListener("click", aoEscolherAutorAcervo);
    document.getElementById("painel-metas").addEventListener("click", aoClicarMetas);
    dialog.addEventListener("close", () => {
        exclusaoPendente = null;
    });
    capaImagem.addEventListener("load", () => {
        if (!capaImagem.getAttribute("src")) {
            return;
        }
        capaImagem.hidden = false;
        capaLegenda.hidden = true;
    });
    capaImagem.addEventListener("error", () => {
        if (!capaImagem.getAttribute("src")) {
            return;
        }
        capaImagem.hidden = true;
        capaLegenda.hidden = false;
        capaLegenda.textContent = "Capa indisponível";
    });
    mostrarAba(abaAtual);
    carregarLivros();
});

function aoTrocarAba(evento) {
    const botao = evento.target.closest("button[data-aba]");
    if (!botao) {
        return;
    }
    mostrarAba(botao.dataset.aba);
}

function mostrarAba(nome) {
    const abas = ["acervo", "autores", "metas"];
    const escolhida = abas.includes(nome) ? nome : "acervo";
    abaAtual = escolhida;
    localStorage.setItem("acervo.aba", escolhida);

    document.querySelectorAll(".aba").forEach((botao) => {
        const ativa = botao.dataset.aba === escolhida;
        botao.classList.toggle("ativo", ativa);
        botao.setAttribute("aria-selected", String(ativa));
    });
    document.querySelectorAll(".aba-painel").forEach((painel) => {
        const ativa = painel.id === `painel-${escolhida}`;
        painel.classList.toggle("ativo", ativa);
        if (ativa) {
            painel.removeAttribute("hidden");
        } else {
            painel.setAttribute("hidden", "");
        }
    });

    if (escolhida === "autores") {
        carregarAutores();
    }
    if (escolhida === "metas") {
        renderizarMetas(acervoCompleto.length ? acervoCompleto : livrosCache);
    }
}

async function carregarLivros(termo = "") {
    const primeira = lista.querySelector(".cartao") === null && !filtrandoAcervo;
    if (primeira) {
        lista.innerHTML = '<p class="vazio">Carregando acervo...</p>';
    }
    try {
        const caminho = termo ? `${API}?termo=${encodeURIComponent(termo)}` : API;
        const livros = await solicitar(caminho);
        filtrandoAcervo = termo !== "";
        renderizar(Array.isArray(livros) ? livros : [], termo);
        if (!filtrandoAcervo) {
            acervoCompleto = [...livrosCache];
        } else {
            const todos = await solicitar(API);
            acervoCompleto = Array.isArray(todos) ? todos : [];
        }
        renderizarMetas(acervoCompleto);
        if (abaAtual === "autores") {
            carregarAutores();
        }
    } catch (erro) {
        livrosCache = [];
        acervoCompleto = [];
        atualizarContador(0);
        lista.innerHTML = '<p class="vazio">Não foi possível carregar o acervo.</p>';
        renderizarMetas([]);
        mostrarAviso(erro.message, "erro");
    }
}

function definirModoAcervo(modo) {
    if (modo !== "grade" && modo !== "lista") {
        return;
    }
    modoAcervo = modo;
    localStorage.setItem("acervo.modo", modo);
    atualizarBotoesModo();
    aplicarClasseModo();
    renderizar(livrosCache, termoAcervoAtual);
}

function atualizarBotoesModo() {
    const botaoGrade = document.getElementById("modo-grade");
    const botaoLista = document.getElementById("modo-lista");
    const grade = modoAcervo === "grade";
    botaoGrade.classList.toggle("ativo", grade);
    botaoLista.classList.toggle("ativo", !grade);
    botaoGrade.setAttribute("aria-pressed", String(grade));
    botaoLista.setAttribute("aria-pressed", String(!grade));
}

function aplicarClasseModo() {
    lista.classList.toggle("grade-acervo", modoAcervo === "grade");
    lista.classList.toggle("lista-acervo", modoAcervo === "lista");
}

function renderizar(livros, termo = "") {
    livrosCache = livros;
    termoAcervoAtual = termo;
    atualizarContador(livros.length);
    aplicarClasseModo();
    if (livros.length === 0) {
        lista.innerHTML = termo
            ? '<p class="vazio">Nenhum livro encontrado no acervo.</p>'
            : '<p class="vazio">Nenhum livro cadastrado. Busque uma obra no fichário e salve.</p>';
        return;
    }

    lista.innerHTML = livros.map((livro) => {
        const capa = livro.temCapa
            ? `<img src="${API}/${livro.id}/capa" alt="" referrerpolicy="no-referrer">`
            : '<span class="capa-cartao" aria-hidden="true"></span>';
        const meta = modoAcervo === "lista"
            ? `<span class="meta-cartao">${escapar([livro.autor, livro.editora, livro.anoPublicacao].filter(Boolean).join(" · ") || "Sem detalhes")}</span>`
            : "";
        return `
            <button type="button" class="cartao" data-id="${livro.id}">
                ${capa}
                <span>
                    <span class="titulo-cartao">${escapar(livro.titulo)}</span>
                    ${meta}
                </span>
            </button>`;
    }).join("");
}

function atualizarContador(total) {
    totalLivros.textContent = total === 1 ? "1 livro" : `${total} livros`;
}

function alternarBuscaCatalogo() {
    const aberto = painelCatalogo.hidden;
    painelCatalogo.hidden = !aberto;
    if (aberto) {
        termoCatalogo.focus();
        return;
    }
    termoCatalogo.value = "";
    carregarLivros();
}

async function buscarObras(carregarMais) {
    const titulo = buscaInput.value.trim();
    if (titulo.length < 2) {
        mostrarAviso("Informe ao menos 2 caracteres do título.", "erro");
        return;
    }
    if (carregarMais && (buscandoMais || !temMaisResultados)) {
        return;
    }

    const pagina = carregarMais ? paginaAtual + 1 : 1;
    const seq = ++buscaSeq;
    if (buscaAbort) {
        buscaAbort.abort();
    }
    buscaAbort = new AbortController();

    if (!carregarMais) {
        consultaAtual = titulo;
        resultados.hidden = false;
        resultados.innerHTML = '<p class="vazio">Buscando...</p>';
        maisResultados.hidden = true;
        limparSelecaoParcial();
    } else {
        buscandoMais = true;
        botaoMais.disabled = true;
        botaoMais.textContent = "Carregando...";
    }

    botaoBuscar.disabled = true;
    try {
        const params = new URLSearchParams({
            titulo,
            pagina: String(pagina),
            limite: String(LIMITE_BUSCA)
        });
        const paginaBusca = await solicitar(`${API}/busca?${params}`, { signal: buscaAbort.signal });
        if (seq !== buscaSeq || titulo !== buscaInput.value.trim()) {
            return;
        }
        const itens = Array.isArray(paginaBusca?.itens) ? paginaBusca.itens : [];
        if (carregarMais) {
            const existentes = new Set(resultadosCache.map(chaveResultado));
            for (const item of itens) {
                const chave = chaveResultado(item);
                if (!existentes.has(chave)) {
                    resultadosCache.push(item);
                    existentes.add(chave);
                }
            }
        } else {
            resultadosCache = itens;
        }
        paginaAtual = paginaBusca?.pagina || pagina;
        temMaisResultados = Boolean(paginaBusca?.temMais);
        renderizarResultados(resultadosCache);
        atualizarBotaoMais();
    } catch (erro) {
        if (erro.name === "AbortError") {
            return;
        }
        if (seq !== buscaSeq) {
            return;
        }
        if (!carregarMais) {
            resultadosCache = [];
            resultados.innerHTML = '<p class="vazio">Não foi possível consultar as bases de livros.</p>';
        }
        mostrarAviso(erro.message, "erro");
        atualizarBotaoMais();
    } finally {
        if (seq === buscaSeq) {
            botaoBuscar.disabled = false;
            buscandoMais = false;
            botaoMais.disabled = false;
            botaoMais.textContent = "Carregar mais";
        }
    }
}

function chaveResultado(obra) {
    return `${obra.isbn || ""}|${obra.titulo || ""}|${obra.editora || ""}|${obra.anoPublicacao || ""}`;
}

function renderizarResultados(obras) {
    if (obras.length === 0) {
        resultados.innerHTML = '<p class="vazio">Nenhum livro encontrado para esse título.</p>';
        return;
    }
    resultados.innerHTML = obras.map((obra, indice) => {
        const capa = obra.capaUrl
            ? `<img class="mini-capa" src="${escapar(obra.capaUrl)}" alt="" referrerpolicy="no-referrer">`
            : '<span class="mini-capa sem-capa" aria-hidden="true"></span>';
        const selo = obra.jaCadastrado ? '<span class="selo-cadastrado">Já no acervo</span>' : "";
        const meta = [obra.autor, obra.editora, obra.anoPublicacao].filter(Boolean).join(" · ");
        return `
            <button type="button" class="resultado" data-indice="${indice}">
                ${capa}
                <span class="resultado-corpo">
                    <span class="resultado-titulo">${escapar(obra.titulo)}</span>
                    ${meta ? `<span class="meta-resultado">${escapar(meta)}</span>` : ""}
                    ${selo}
                </span>
            </button>`;
    }).join("");
}

function atualizarBotaoMais() {
    maisResultados.hidden = !(temMaisResultados && resultadosCache.length > 0);
}

async function aoEscolherResultado(evento) {
    const botao = evento.target.closest("button[data-indice]");
    if (!botao) {
        return;
    }
    const indice = Number(botao.dataset.indice);
    let obra = resultadosCache[indice];
    if (!obra || !obra.titulo) {
        return;
    }
    resultados.querySelectorAll("button.selecionado").forEach((item) => item.classList.remove("selecionado"));
    botao.classList.add("selecionado");
    aplicarSelecao(obra);

    if (obra.isbn && precisaComplemento(obra)) {
        try {
            const extra = await solicitar(`${API}/busca/complemento?${new URLSearchParams({ isbn: obra.isbn })}`);
            if (!selecionado || selecionado.isbn !== obra.isbn) {
                return;
            }
            obra = mesclarObra(obra, extra);
            resultadosCache[indice] = obra;
            aplicarSelecao(obra);
        } catch (erro) {
            // Mantém a seleção já exibida; o complemento é opcional.
        }
    }
}

function precisaComplemento(obra) {
    const capas = Array.isArray(obra.capas) ? obra.capas.length : 0;
    const sinopse = (obra.descricao || "").trim();
    return capas < 2 || sinopse.length < 80 || !obra.edicao || !obra.numeroPaginas;
}

function mesclarObra(base, extra) {
    if (!extra) {
        return base;
    }
    const capas = Array.from(new Set([...(base.capas || []), ...(extra.capas || []), base.capaUrl, extra.capaUrl].filter(Boolean)));
    return {
        ...base,
        autor: base.autor || extra.autor,
        editora: base.editora || extra.editora,
        anoPublicacao: base.anoPublicacao ?? extra.anoPublicacao,
        edicao: base.edicao || extra.edicao,
        numeroPaginas: base.numeroPaginas ?? extra.numeroPaginas,
        descricao: ((extra.descricao || "").length >= (base.descricao || "").length ? extra.descricao : base.descricao) || base.descricao,
        genero: base.genero || extra.genero,
        capaUrl: base.capaUrl || extra.capaUrl,
        capas,
        origem: extra.origem || base.origem,
        jaCadastrado: base.jaCadastrado || extra.jaCadastrado,
        idExistente: base.idExistente || extra.idExistente
    };
}

function aplicarSelecao(obra) {
    selecionado = obra;
    capaEscolhida = obra.capaUrl || (Array.isArray(obra.capas) && obra.capas[0]) || null;
    tituloSelecionado.textContent = obra.titulo;
    botaoSalvar.disabled = false;
    botaoSalvar.textContent = obra.jaCadastrado ? "Atualizar no acervo" : "Salvar no acervo";
    renderizarOpcoesCapa(obra);
    if (capaEscolhida) {
        mostrarCapa(capaEscolhida);
    } else {
        resetarCapa();
    }
}

function renderizarOpcoesCapa(obra) {
    const capas = Array.from(new Set([...(obra.capas || []), obra.capaUrl].filter(Boolean)));
    if (capas.length <= 1) {
        opcoesCapa.hidden = true;
        gradeCapas.innerHTML = "";
        return;
    }
    opcoesCapa.hidden = false;
    gradeCapas.innerHTML = capas.map((url, indice) => `
        <button type="button" class="capa-opcao${url === capaEscolhida ? " selecionada" : ""}" data-capa="${indice}" title="Capa ${indice + 1}">
            <img src="${escapar(url)}" alt="" referrerpolicy="no-referrer">
        </button>
    `).join("");
    gradeCapas.dataset.capas = JSON.stringify(capas);
}

function aoEscolherCapa(evento) {
    const botao = evento.target.closest("button[data-capa]");
    if (!botao || !selecionado) {
        return;
    }
    let capas = [];
    try {
        capas = JSON.parse(gradeCapas.dataset.capas || "[]");
    } catch (erro) {
        capas = [];
    }
    const url = capas[Number(botao.dataset.capa)];
    if (!url) {
        return;
    }
    capaEscolhida = url;
    gradeCapas.querySelectorAll("button").forEach((item) => item.classList.remove("selecionada"));
    botao.classList.add("selecionada");
    mostrarCapa(url);
}

async function salvarLivro(evento) {
    evento.preventDefault();
    if (!selecionado || !selecionado.titulo) {
        mostrarAviso("Selecione um livro da busca antes de salvar.", "erro");
        return;
    }
    botaoSalvar.disabled = true;
    try {
        const dados = await montarCadastro(selecionado);
        if (selecionado.jaCadastrado && selecionado.idExistente) {
            await solicitar(`${API}/${selecionado.idExistente}`, { method: "PUT", body: dados });
            mostrarAviso("Livro atualizado no acervo.", "sucesso");
        } else {
            await solicitar(API, { method: "POST", body: dados });
            mostrarAviso("Livro salvo no acervo.", "sucesso");
        }
        limparSelecao();
        await carregarLivros(termoCatalogo.value.trim());
    } catch (erro) {
        mostrarAviso(erro.message, "erro");
        botaoSalvar.disabled = false;
    }
}

async function montarCadastro(obra) {
    let quantidade = 1;
    let statusLeitura = null;
    if (obra.jaCadastrado && obra.idExistente) {
        const atual = livrosCache.find((item) => item.id === obra.idExistente)
            || await solicitar(`${API}/${obra.idExistente}`);
        quantidade = atual.quantidade ?? 1;
        statusLeitura = atual.statusLeitura || null;
    }
    return {
        titulo: obra.titulo,
        autor: obra.autor || null,
        isbn: obra.isbn || null,
        anoPublicacao: obra.anoPublicacao ?? null,
        genero: obra.genero || null,
        editora: obra.editora || null,
        quantidade,
        edicao: obra.edicao || null,
        numeroPaginas: obra.numeroPaginas ?? null,
        descricao: obra.descricao || null,
        capaOrigem: capaEscolhida || obra.capaUrl || null,
        statusLeitura
    };
}

function limparSelecaoParcial() {
    selecionado = null;
    capaEscolhida = null;
    tituloSelecionado.textContent = "Nenhum livro selecionado";
    botaoSalvar.disabled = true;
    botaoSalvar.textContent = "Salvar no acervo";
    opcoesCapa.hidden = true;
    gradeCapas.innerHTML = "";
    resetarCapa();
}

function limparSelecao() {
    limparSelecaoParcial();
    resultadosCache = [];
    paginaAtual = 0;
    temMaisResultados = false;
    consultaAtual = "";
    resultados.hidden = true;
    resultados.innerHTML = "";
    maisResultados.hidden = true;
    buscaInput.value = "";
}

function mostrarCapa(src) {
    capaLegenda.hidden = true;
    capaImagem.hidden = true;
    capaImagem.alt = "Capa do livro";
    capaImagem.src = src;
}

function resetarCapa() {
    capaImagem.hidden = true;
    capaImagem.alt = "";
    capaImagem.removeAttribute("src");
    capaLegenda.hidden = false;
    capaLegenda.textContent = "Sem capa";
}

async function aoClicarNoAcervo(evento) {
    const cartao = evento.target.closest("button[data-id]");
    if (!cartao) {
        return;
    }
    try {
        const livro = await solicitar(`${API}/${cartao.dataset.id}`);
        abrirFicha(livro);
    } catch (erro) {
        mostrarAviso(erro.message, "erro");
    }
}

function abrirFicha(livro) {
    fichaAberta = livro;
    document.getElementById("detalhe-titulo").textContent = livro.titulo;
    document.getElementById("detalhe-autor").textContent = texto(livro.autor);
    document.getElementById("detalhe-editora").textContent = texto(livro.editora);
    document.getElementById("detalhe-ano").textContent = texto(livro.anoPublicacao);
    document.getElementById("detalhe-edicao").textContent = texto(livro.edicao);
    document.getElementById("detalhe-isbn").textContent = texto(livro.isbn);
    document.getElementById("detalhe-paginas").textContent = texto(livro.numeroPaginas);
    document.getElementById("detalhe-genero").textContent = texto(livro.genero);
    detalheDescricao.value = livro.descricao || "";
    renderizarBotoesStatus(detalheStatus, livro.statusLeitura || null, livro.id);
    if (livro.temCapa) {
        detalheCapa.alt = `Capa de ${livro.titulo}`;
        detalheCapa.src = `${API}/${livro.id}/capa`;
        detalheCapa.hidden = false;
        detalheSemCapa.hidden = true;
    } else {
        detalheCapa.hidden = true;
        detalheCapa.removeAttribute("src");
        detalheSemCapa.hidden = false;
    }
    ficha.showModal();
}

async function salvarSinopse() {
    if (!fichaAberta) {
        return;
    }
    const botao = document.getElementById("salvar-sinopse");
    botao.disabled = true;
    try {
        const atualizado = await solicitar(`${API}/${fichaAberta.id}`, {
            method: "PUT",
            body: {
                titulo: fichaAberta.titulo,
                autor: fichaAberta.autor || null,
                isbn: fichaAberta.isbn || null,
                anoPublicacao: fichaAberta.anoPublicacao ?? null,
                genero: fichaAberta.genero || null,
                editora: fichaAberta.editora || null,
                quantidade: fichaAberta.quantidade ?? 1,
                edicao: fichaAberta.edicao || null,
                numeroPaginas: fichaAberta.numeroPaginas ?? null,
                descricao: detalheDescricao.value.trim() || null,
                capaOrigem: null,
                statusLeitura: fichaAberta.statusLeitura || null
            }
        });
        fichaAberta = atualizado;
        sincronizarLivroLocal(atualizado);
        mostrarAviso("Sinopse atualizada.", "sucesso");
    } catch (erro) {
        mostrarAviso(erro.message, "erro");
    } finally {
        botao.disabled = false;
    }
}

function texto(valor) {
    if (valor === null || valor === undefined || String(valor).trim() === "") {
        return "Não informado";
    }
    return String(valor);
}

function pedirExclusao() {
    if (!fichaAberta) {
        return;
    }
    exclusaoPendente = fichaAberta;
    ficha.close();
    confirmaTexto.textContent = `Deseja excluir "${fichaAberta.titulo}"? Esta ação não pode ser desfeita.`;
    dialog.showModal();
}

async function confirmarExclusao() {
    const livro = exclusaoPendente;
    dialog.close();
    if (!livro) {
        return;
    }
    try {
        await solicitar(`${API}/${livro.id}`, { method: "DELETE" });
        if (selecionado && selecionado.idExistente === livro.id) {
            limparSelecao();
        }
        mostrarAviso("Livro excluído com sucesso.", "sucesso");
        await carregarLivros(termoCatalogo.value.trim());
    } catch (erro) {
        mostrarAviso(erro.message, "erro");
    }
}

async function solicitar(caminho, { method = "GET", body, signal } = {}) {
    const opcoes = {
        method,
        headers: { Accept: "application/json" },
        signal
    };
    if (body !== undefined) {
        opcoes.headers["Content-Type"] = "application/json; charset=utf-8";
        opcoes.body = JSON.stringify(body);
    }

    let response;
    try {
        response = await fetch(caminho, opcoes);
    } catch (erro) {
        if (erro && erro.name === "AbortError") {
            throw erro;
        }
        throw new Error("Não foi possível comunicar com o servidor.");
    }

    if (response.status === 204) {
        return null;
    }

    const conteudo = await response.text();
    let corpo = null;
    if (conteudo) {
        try {
            corpo = JSON.parse(conteudo);
        } catch (erro) {
            corpo = null;
        }
    }

    if (!response.ok) {
        throw new Error(mensagemDeErro(corpo, response.status));
    }
    return corpo;
}

function mensagemDeErro(corpo, status) {
    if (corpo && Array.isArray(corpo.campos) && corpo.campos.length > 0) {
        return corpo.campos.map((item) => item.mensagem).filter(Boolean).join(" ");
    }
    if (corpo && corpo.mensagem) {
        return corpo.mensagem;
    }
    if (status === 404) {
        return "Registro não encontrado.";
    }
    return "Não foi possível concluir a operação.";
}

function mostrarAviso(mensagem, tipo) {
    aviso.hidden = false;
    aviso.className = `aviso ${tipo}`;
    aviso.textContent = mensagem;
    clearTimeout(avisoTimer);
    if (tipo === "sucesso") {
        avisoTimer = setTimeout(() => {
            aviso.hidden = true;
        }, 5200);
    }
}

function escapar(valor) {
    return String(valor ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;");
}

function sincronizarLivroLocal(atualizado) {
    const idx = livrosCache.findIndex((item) => item.id === atualizado.id);
    if (idx >= 0) {
        livrosCache[idx] = atualizado;
    }
    const idxCompleto = acervoCompleto.findIndex((item) => item.id === atualizado.id);
    if (idxCompleto >= 0) {
        acervoCompleto[idxCompleto] = atualizado;
    } else {
        acervoCompleto.push(atualizado);
    }
    renderizar(livrosCache, termoAcervoAtual);
    renderizarMetas(acervoCompleto);
    if (fichaAberta && fichaAberta.id === atualizado.id) {
        fichaAberta = atualizado;
        renderizarBotoesStatus(detalheStatus, atualizado.statusLeitura || null, atualizado.id);
    }
}

function renderizarBotoesStatus(container, statusAtual, livroId) {
    const botoes = STATUS_LEITURA.map((item) => `
        <button type="button" class="status-botao${item.codigo === statusAtual ? " ativo" : ""}"
            data-status="${item.codigo}" data-id="${livroId}">${item.rotulo}</button>
    `).join("");
    container.innerHTML = `
        ${botoes}
        <button type="button" class="status-botao${!statusAtual ? " ativo" : ""}"
            data-status="" data-id="${livroId}">Sem etiqueta</button>
    `;
}

async function aoClicarStatusDetalhe(evento) {
    const botao = evento.target.closest("button[data-id][data-status]");
    if (!botao || !fichaAberta) {
        return;
    }
    await definirStatusLeitura(Number(botao.dataset.id), botao.dataset.status || null, botao);
}

async function aoClicarMetas(evento) {
    const botao = evento.target.closest("button[data-id][data-status]");
    if (!botao) {
        return;
    }
    await definirStatusLeitura(Number(botao.dataset.id), botao.dataset.status || null, botao);
}

async function definirStatusLeitura(id, status, botaoOrigem) {
    if (botaoOrigem) {
        botaoOrigem.disabled = true;
    }
    try {
        const atualizado = await solicitar(`${API}/${id}/status`, {
            method: "PATCH",
            body: { statusLeitura: status }
        });
        sincronizarLivroLocal(atualizado);
        mostrarAviso("Meta de leitura atualizada.", "sucesso");
    } catch (erro) {
        mostrarAviso(erro.message, "erro");
        if (botaoOrigem) {
            botaoOrigem.disabled = false;
        }
    }
}

function renderizarMetas(livros) {
    const grupos = {
        quero_ler: [],
        lendo: [],
        lido: [],
        sem: []
    };
    for (const livro of livros) {
        if (livro.statusLeitura && grupos[livro.statusLeitura]) {
            grupos[livro.statusLeitura].push(livro);
        } else {
            grupos.sem.push(livro);
        }
    }

    document.getElementById("coluna-quero_ler").innerHTML = htmlMetaLista(grupos.quero_ler, "quero_ler");
    document.getElementById("coluna-lendo").innerHTML = htmlMetaLista(grupos.lendo, "lendo");
    document.getElementById("coluna-lido").innerHTML = htmlMetaLista(grupos.lido, "lido");
    document.getElementById("coluna-sem").innerHTML = htmlMetaLista(grupos.sem, null);

    document.getElementById("contagem-quero_ler").textContent = String(grupos.quero_ler.length);
    document.getElementById("contagem-lendo").textContent = String(grupos.lendo.length);
    document.getElementById("contagem-lido").textContent = String(grupos.lido.length);
    document.getElementById("contagem-sem").textContent = String(grupos.sem.length);

    const etiquetados = grupos.quero_ler.length + grupos.lendo.length + grupos.lido.length;
    resumoMetas.textContent = etiquetados === 1 ? "1 etiquetado" : `${etiquetados} etiquetados`;
}

function htmlMetaLista(livros, statusColuna) {
    if (livros.length === 0) {
        return '<p class="vazio">Nenhum livro nesta lista.</p>';
    }
    return livros.map((livro) => {
        const capa = livro.temCapa
            ? `<img src="${API}/${livro.id}/capa" alt="" referrerpolicy="no-referrer">`
            : '<span class="capa-cartao" aria-hidden="true"></span>';
        return `
            <article class="meta-card">
                ${capa}
                <div>
                    <span class="meta-titulo">${escapar(livro.titulo)}</span>
                    <span class="meta-autor">${escapar(livro.autor || "Autor não informado")}</span>
                    <div class="status-grupo">
                        ${STATUS_LEITURA.map((item) => `
                            <button type="button" class="status-botao${item.codigo === livro.statusLeitura ? " ativo" : ""}"
                                data-status="${item.codigo}" data-id="${livro.id}">${item.rotulo}</button>
                        `).join("")}
                        ${statusColuna ? `
                            <button type="button" class="status-botao" data-status="" data-id="${livro.id}">Limpar</button>
                        ` : ""}
                    </div>
                </div>
            </article>`;
    }).join("");
}

async function carregarAutores() {
    try {
        const autores = await solicitar(API_AUTORES);
        autoresCache = Array.isArray(autores) ? autores : [];
        renderizarAutores(autoresCache);
    } catch (erro) {
        autoresCache = [];
        listaAutores.innerHTML = '<p class="vazio">Não foi possível carregar os autores do acervo.</p>';
        totalAutores.textContent = "0 autores";
        mostrarAviso(erro.message, "erro");
    }
}

function renderizarAutores(autores) {
    totalAutores.textContent = autores.length === 1 ? "1 autor" : `${autores.length} autores`;
    if (autores.length === 0) {
        listaAutores.innerHTML = '<p class="vazio">Nenhum autor cadastrado no acervo.</p>';
        return;
    }
    listaAutores.innerHTML = autores.map((autor) => {
        const selecionadoClasse = autorSelecionado && autorSelecionado.toLowerCase() === autor.nome.toLowerCase()
            ? " selecionado"
            : "";
        const livros = autor.livros === 1 ? "1 livro" : `${autor.livros} livros`;
        return `
            <button type="button" class="autor-item${selecionadoClasse}" data-autor="${escapar(autor.nome)}">
                <span class="autor-nome">${escapar(autor.nome)}</span>
                <span class="autor-meta">${livros}</span>
            </button>`;
    }).join("");
}

function aoEscolherAutorAcervo(evento) {
    const botao = evento.target.closest("button[data-autor]");
    if (!botao) {
        return;
    }
    const nome = botao.dataset.autor;
    buscaAutor.value = nome;
    buscarBiografia(nome);
}

async function buscarBiografia(nome) {
    const consulta = (nome || "").trim();
    if (consulta.length < 2) {
        mostrarAviso("Informe ao menos 2 caracteres do nome do autor.", "erro");
        return;
    }
    autorSelecionado = consulta;
    renderizarAutores(autoresCache);
    if (wikiAbort) {
        wikiAbort.abort();
    }
    wikiAbort = new AbortController();
    botaoBuscarAutor.disabled = true;
    biografiaAutor.innerHTML = '<p class="vazio">Consultando a Wikipedia...</p>';
    try {
        const bio = await solicitar(`${API_AUTORES}/wiki?${new URLSearchParams({ nome: consulta })}`, {
            signal: wikiAbort.signal
        });
        renderizarBiografia(bio, consulta);
    } catch (erro) {
        if (erro.name === "AbortError") {
            return;
        }
        biografiaAutor.innerHTML = `<p class="vazio">${escapar(erro.message)}</p>`;
        mostrarAviso(erro.message, "erro");
    } finally {
        botaoBuscarAutor.disabled = false;
    }
}

function renderizarBiografia(bio, consulta) {
    if (!bio || !bio.encontrado) {
        biografiaAutor.innerHTML = `
            <p class="vazio">Não encontramos uma biografia na Wikipedia em português para “${escapar(consulta)}”.</p>`;
        return;
    }
    const foto = bio.imagemUrl
        ? `<img class="bio-foto" src="${escapar(bio.imagemUrl)}" alt="" referrerpolicy="no-referrer">`
        : '<div class="bio-foto vazia">Sem imagem</div>';
    const descricao = bio.descricao
        ? `<p class="bio-descricao">${escapar(bio.descricao)}</p>`
        : "";
    const link = bio.paginaUrl
        ? `<a class="bio-link" href="${escapar(bio.paginaUrl)}" target="_blank" rel="noopener noreferrer">Abrir na Wikipedia</a>`
        : "";
    biografiaAutor.innerHTML = `
        <div class="bio-cabeca">
            ${foto}
            <div>
                <h3 class="bio-titulo">${escapar(bio.titulo || consulta)}</h3>
                ${descricao}
                ${link}
            </div>
        </div>
        <p class="bio-extrato">${escapar(bio.extrato || "")}</p>
    `;
}
