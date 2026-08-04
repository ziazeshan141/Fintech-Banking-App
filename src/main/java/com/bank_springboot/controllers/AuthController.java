package com.bank_springboot.controllers;

import com.bank_springboot.mailMessenger.MailMessenger;
import com.bank_springboot.models.User;
import com.bank_springboot.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.UUID;

@Controller
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MailMessenger mailMessenger;

    /**
     * Display login page.
     *
     * Token generation is handled locally using UUID.
     * This avoids calling MailMessenger while loading the login page.
     */
    @GetMapping("/login")
    public ModelAndView getLogin() {

        System.out.println("In Login Page Controller");

        ModelAndView loginPage = new ModelAndView("login");

        String token = UUID.randomUUID().toString();

        loginPage.addObject("token", token);
        loginPage.addObject("PageTitle", "Login");

        return loginPage;
    }

    /**
     * Process login form.
     */
    @PostMapping("/login")
    public String login(
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("_token") String token,
            Model model,
            HttpSession session
    ) throws MessagingException {

        /*
         * Validate input values.
         * Null checks must be performed before calling trim() or isEmpty().
         */
        if (email == null || email.trim().isEmpty()
                || password == null || password.trim().isEmpty()) {

            model.addAttribute(
                    "error",
                    "Username or password cannot be empty"
            );

            return "login";
        }

        email = email.trim();

        /*
         * Check whether the email exists.
         */
        String databaseEmail = userRepository.getUserEmail(email);

        System.out.println("Email: " + databaseEmail);

        if (databaseEmail == null) {

            model.addAttribute(
                    "error",
                    "Incorrect username or password"
            );

            return "login";
        }

        /*
         * Retrieve and validate the password.
         */
        String databasePassword =
                userRepository.getUserPassword(databaseEmail);

        if (databasePassword == null
                || !BCrypt.checkpw(password, databasePassword)) {

            model.addAttribute(
                    "error",
                    "Incorrect username or password"
            );

            return "login";
        }

        /*
         * Check whether the user account is verified.
         */
        int verified = userRepository.isVerified(databaseEmail);

        System.out.println("Account verification status: " + verified);

        if (verified != 1) {

            String verificationToken =
                    userRepository.checkToken(databaseEmail);

            System.out.println(
                    "Auth Token: " + verificationToken
            );

            if (verificationToken != null
                    && !verificationToken.trim().isEmpty()) {

                mailMessenger.sendVerificationEmail(
                        databaseEmail,
                        verificationToken
                );
            }

            model.addAttribute(
                    "error",
                    "This account is not yet verified. "
                            + "Please check your email and verify the account."
            );

            return "login";
        }

        /*
         * Retrieve the complete user record.
         */
        User user =
                userRepository.getUserDetails(databaseEmail);

        if (user == null) {

            model.addAttribute(
                    "error",
                    "Unable to retrieve user account details"
            );

            return "error";
        }

        /*
         * Create authenticated session.
         */
        session.setAttribute("user", user);
        session.setAttribute("token", token);
        session.setAttribute("authenticated", true);

        return "redirect:/app/dashboard";
    }

    /**
     * Log out and invalidate the current session.
     */
    @GetMapping("/logout")
    public String logout(
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes
    ) {

        HttpSession session = request.getSession(false);

        if (session != null) {

            session.removeAttribute("user");
            session.removeAttribute("email");
            session.removeAttribute("password");
            session.removeAttribute("token");
            session.removeAttribute("authenticated");

            session.invalidate();
        }

        redirectAttributes.addFlashAttribute(
                "logged_out",
                "Logged out successfully"
        );

        return "redirect:/login?logout=true";
    }
}