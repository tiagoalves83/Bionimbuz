package br.unb.cic.bionimbus.rest.resource;

import br.unb.cic.bionimbus.jobcontroller.JobController;
import br.unb.cic.bionimbus.persistence.dao.FileDao;
import br.unb.cic.bionimbus.persistence.dao.UserDao;
import br.unb.cic.bionimbus.rest.model.UploadedFileInfo;
import br.unb.cic.bionimbus.rest.model.User;
import java.util.Calendar;
import java.util.List;

import javax.persistence.NoResultException;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import br.unb.cic.bionimbus.rest.request.LoginRequest;
import br.unb.cic.bionimbus.rest.request.LogoutRequest;
import br.unb.cic.bionimbus.rest.request.RequestInfo;
import br.unb.cic.bionimbus.rest.request.SignUpRequest;
import br.unb.cic.bionimbus.rest.response.LoginResponse;
import br.unb.cic.bionimbus.rest.response.LogoutResponse;
import br.unb.cic.bionimbus.rest.response.ResponseInfo;
import br.unb.cic.bionimbus.rest.response.SignUpResponse;
//import br.unb.cic.bionimbus.usercontroller.LoggedUsers;

@Path("/rest")
public class UserResource extends AbstractResource {

    private final UserDao userDao;
    private final FileDao fileInfoDao;

    public UserResource(JobController jobController) {
        this.jobController = jobController;
        this.userDao = new UserDao();
        this.fileInfoDao = new FileDao();
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public LoginResponse login(LoginRequest loginRequest) {
        User requestUser = loginRequest.getUser();
        LoginResponse response = new LoginResponse();

        LOGGER.info("Login request received: [login: " + requestUser.getLogin() + ", password: " + requestUser.getPassword().toString().charAt(0) + "*****]");

        // Verifies if the request user exists on database
        User responseUser = null;

        try {
            responseUser = userDao.findByLogin(requestUser.getLogin());

        } catch (NoResultException e) {
            LOGGER.info("User " + requestUser.getLogin() + " not found");

        } catch (Exception e) {
            LOGGER.error("[Exception - " + e.getMessage() + "] UserResource.login()");
        }

        if (responseUser != null && (requestUser.getPassword().equals(responseUser.getPassword()))) {
            // Adds user to the logged users list
            // LoggedUsers.addLoggedUser(responseUser);

            List<UploadedFileInfo> userFiles = fileInfoDao.listByUserId(responseUser.getId());
            responseUser.setFiles(userFiles);

            // Encrypts secretKey with bCrypt encoder
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            String secretKey = encoder.encode(requestUser.getLogin() + Calendar.getInstance());

            responseUser.setSecurityToken(secretKey);

            // Sets response populated user
            response.setUser(responseUser);
        } else {
            response.setUser(loginRequest.getUser());
        }

        //LoggedUsers.printLoggedUsersMap();
        return response;
    }

    @POST
    @Path("/logout")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public LogoutResponse logout(LogoutRequest logoutRequest) {
        LOGGER.info("Logout request received: [login: " + logoutRequest.getUser().getLogin() + "]");

        LogoutResponse response = new LogoutResponse();

        // Removes the user from the logged users' list
        //LoggedUsers.removeLoggedUser(logoutRequest.getUser().getLogin());
        response.setLogoutSuccess(true);

        return response;
    }

    @POST
    @Path("/signup")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public SignUpResponse signUp(SignUpRequest request) {
        LOGGER.info("Sign up request received. [login: " + request.getUser().getLogin() + "]");

        userDao.exists(request.getUser().getLogin());

        SignUpResponse response = new SignUpResponse();
        response.setAdded(true);

        return response;
    }

    @Override
    public ResponseInfo handleIncoming(RequestInfo request) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
