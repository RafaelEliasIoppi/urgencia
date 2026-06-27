package br.gov.saude.sgpur.web;

import br.gov.saude.sgpur.domain.Perfil;
import br.gov.saude.sgpur.domain.Usuario;
import br.gov.saude.sgpur.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @ModelAttribute("perfis")
    public Perfil[] perfis() {
        return Perfil.values();
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("usuarios", service.listar());
        return "usuarios/lista";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        model.addAttribute("usuario", new Usuario());
        model.addAttribute("edicao", false);
        return "usuarios/form";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        model.addAttribute("usuario", service.buscar(id));
        model.addAttribute("edicao", true);
        return "usuarios/form";
    }

    @PostMapping
    public String criar(@Valid @ModelAttribute("usuario") Usuario usuario, BindingResult result,
                        @RequestParam String senha, Model model, RedirectAttributes ra) {
        if (senha == null || senha.isBlank()) {
            result.rejectValue("senha", "obrigatorio", "Informe a senha.");
        }
        if (result.hasErrors()) {
            model.addAttribute("edicao", false);
            return "usuarios/form";
        }
        try {
            service.criar(usuario, senha);
        } catch (IllegalArgumentException e) {
            model.addAttribute("edicao", false);
            model.addAttribute("erro", e.getMessage());
            return "usuarios/form";
        }
        ra.addFlashAttribute("msg", "Usuario criado.");
        return "redirect:/usuarios";
    }

    @PostMapping("/{id}/editar")
    public String atualizar(@PathVariable Long id, @ModelAttribute("usuario") Usuario form,
                            @RequestParam(required = false) String senha, RedirectAttributes ra) {
        service.atualizar(id, form, senha);
        ra.addFlashAttribute("msg", "Usuario atualizado.");
        return "redirect:/usuarios";
    }

    @PostMapping("/{id}/alternar-ativo")
    public String alternarAtivo(@PathVariable Long id, RedirectAttributes ra) {
        service.alternarAtivo(id);
        ra.addFlashAttribute("msg", "Situacao do usuario atualizada.");
        return "redirect:/usuarios";
    }
}
